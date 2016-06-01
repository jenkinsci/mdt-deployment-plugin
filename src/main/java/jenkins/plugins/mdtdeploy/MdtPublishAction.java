package jenkins.plugins.mdtdeploy;

import com.squareup.okhttp.*;
import jenkins.plugins.mdtdeploy.util.SSLSocketFactoryManager;
import org.apache.commons.lang.NotImplementedException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import hudson.ProxyConfiguration;
import java.net.Proxy;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import java.nio.file.Paths;
import java.nio.file.Path;


@SuppressWarnings("UnusedDeclaration") // This class will be loaded using its Descriptor.
public class MdtPublishAction extends Notifier {
    private boolean deployOnLatest;
    private transient String deployFilePath;
    private transient String apiKey;
    private transient String mdtServerHostname;

    public boolean getDeployOnLatest(){
        return deployOnLatest;
    }

    @DataBoundConstructor
    public MdtPublishAction(boolean deployOnLatest) {
        this.deployOnLatest = deployOnLatest;
        LOGGER.log(Level.ALL,"MdtPublishAction");
    }

    public void configureDeployInfos(JobPropertyImpl pp){
        if (pp!=null){
            apiKey = pp.apiKey;
            deployFilePath = pp.deployFile;
        }
    }

    String normalizePath(String path){
        return Paths.get(path).toString();
    }


    public boolean performDeploy(List<Run<? , ?>.Artifact> artifacts,TaskListener listener){
        mdtServerHostname = GlobalConfigurationMdtDeploy.get().getUrl();
        mdtServerHostname = mdtServerHostname == null ? "" : mdtServerHostname.trim();

        apiKey = apiKey == null ? "" : apiKey.trim();
        deployFilePath = deployFilePath == null ? "" : deployFilePath.trim();

        if (mdtServerHostname.isEmpty()){
            listener.getLogger().println("MDT Deploy: MDT server not set, check your config !");
            return  false;
        }
        if (apiKey.isEmpty()){
            listener.getLogger().println("MDT Deploy: API key, check your config !");
            return  false;
        }
        if ( deployFilePath.isEmpty()){
            listener.getLogger().println("MDT Deploy: Deployment json file, check your config !");
            return  false;
        }

        boolean latest = deployOnLatest;
        String deployFilename = normalizePath(deployFilePath);

        //sort artifact by path
        HashMap<String,Run.Artifact> sortedArtifact = new HashMap<String,Run.Artifact>();
        for (Run.Artifact artifact :  artifacts) {
            sortedArtifact.put(normalizePath(artifact.relativePath),artifact);
        }

        //load deploy file
        Run.Artifact jsonFile = sortedArtifact.get(deployFilename);
        if (jsonFile == null){
            listener.getLogger().println("MDT Deploy: Deployment json file not found ("+deployFilename+")");
            return  false;
        }

        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(new FileReader(jsonFile.getFile()));
            if (!(obj instanceof JSONArray)){
                JSONArray array = new JSONArray();
                array.add(obj);
                obj = array;
            }
            JSONArray jsonArray = (JSONArray)obj;
            for (Object object :  jsonArray) {
                JSONObject jsonObject = (JSONObject)object;
                Run.Artifact artifact = findArtifact(jsonFile,Paths.get(jsonObject.get("file").toString()).toString(),sortedArtifact);
                if (artifact !=null){
                    if(!deleteArtifact(artifact,jsonObject,latest,listener)){
                        listener.getLogger().println("Error deploying artifact "+jsonObject.get("file")+". Aborting!");
                        return false;
                    }

                    if(!sendArtifact(artifact,jsonObject,latest,listener)){
                        listener.getLogger().println("Error deploying artifact "+jsonObject.get("file")+". Aborting!");
                        return false;
                    }else {
                        listener.getLogger().println("<b>Artifact "+jsonObject.get("file")+" deployed successfully</b>");
                    }
                }else {
                    listener.getLogger().println("Unable to find artifact "+jsonObject.get("file"));
                    return false;
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        configureDeployInfos((JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class));
        boolean latest = deployOnLatest;

        return performDeploy(build.getArtifacts(),listener);
    }

    private Run.Artifact findArtifact(Run.Artifact deployFileArtifact, String relativeNameFromDeployFile,HashMap<String,Run.Artifact> sortedArtifacts){
        Path  pathFromDeployFile = Paths.get(relativeNameFromDeployFile);
        if (pathFromDeployFile.isAbsolute()){
            //find artifact by full path
            for (Run.Artifact artifact :  sortedArtifacts.values()) {
                if (artifact.getFile().getAbsolutePath() == pathFromDeployFile.toString()) {
                    return artifact;
                }
            }
            return null;
        }else {
            relativeNameFromDeployFile = normalizePath(relativeNameFromDeployFile);
            //compute artifact 'full' relative path
            String relativePath = Paths.get(deployFileArtifact.relativePath).resolveSibling(relativeNameFromDeployFile).toString();
            LOGGER.log(Level.ALL,"find artifact "+relativePath);
            return  sortedArtifacts.get(relativePath);
        }
    }

    private boolean deleteArtifact(Run.Artifact artifact,JSONObject jsonInfo,boolean latest,TaskListener listener){
        try {
            String url = mdtServerHostname;
            if (latest){
                url+= "/api/in/v1/artifacts/"+apiKey+"/last/"+ jsonInfo.get("name");
            }else {
                url += "/api/in/v1/artifacts/"+apiKey+"/"+ jsonInfo.get("branch")+"/"+ jsonInfo.get("version")+"/"+ jsonInfo.get("name");
            }
            return sendRequest(url,"DELETE",null, Arrays.asList(404,200),listener);
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
        }
        return false;
    }

    private boolean sendArtifact(Run.Artifact artifact,JSONObject jsonInfo,boolean latest,TaskListener listener){
        try {
            String url = mdtServerHostname;
            if (latest){
                url+= "/api/in/v1/artifacts/"+apiKey+"/last/"+ jsonInfo.get("name");
            }else {
                url += "/api/in/v1/artifacts/"+apiKey+"/"+ jsonInfo.get("branch")+"/"+ jsonInfo.get("version")+"/"+ jsonInfo.get("name");
            }
            MultipartBuilder multipart = new MultipartBuilder();
            multipart.type(MultipartBuilder.FORM);
            //multipart.type(MediaType.parse("multipart/form-data;charset=UTF-8"));
            multipart.addFormDataPart("artifactFile", artifact.getFileName(),
                    RequestBody.create(MediaType.parse("application/octet-stream"), artifact.getFile()));

            return sendRequest(url,"POST",multipart, Arrays.asList(200),listener);

        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
        }
        return false;
    }


    private boolean sendRequest(String url,String method,MultipartBuilder multipart,List<Integer> acceptedResturnCode,TaskListener listener) throws IOException{
        OkHttpClient client = new OkHttpClient();
        ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
        if (proxyConfig != null){
            Proxy proxy =  proxyConfig.createProxy(url);
            client.setProxy(proxy);
        }
        client.setConnectTimeout(30, TimeUnit.SECONDS);
        client.setReadTimeout(60, TimeUnit.SECONDS);
        boolean sslCheck =  GlobalConfigurationMdtDeploy.get().getDisableCheckSSL();
        if (sslCheck){
            client.setHostnameVerifier(SSLSocketFactoryManager.createNullHostnameVerifier());
            client.setSslSocketFactory(SSLSocketFactoryManager.createDefaultSslSocketFactory());
        }

        Request.Builder builder = new Request.Builder();
        builder.url(url);

        switch (method){
            case "DELETE":
                builder.delete();
                break;
            case "POST":
                builder.post(multipart.build());
                break;
            default:
                throw  new NotImplementedException();
        }

        Request request = builder.build();
        listener.getLogger().println(String.format("---> %s Artifact %s", method,url));

        long start = System.nanoTime();
        Response response = client.newCall(request).execute();
        long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        listener.getLogger()
                .println(String.format("<--- %s %s (%sms)", response.code(), response.message(), time));

        if (acceptedResturnCode.contains(response.code())){
            return true;
        }else {
            listener.getLogger().println(response.body().string());
        }
        return false;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public Descriptor getDescriptor() {
        return (Descriptor) super.getDescriptor();
    }

    @Extension
    public static final class Descriptor extends BuildStepDescriptor<Publisher> {

        public String url;
        public boolean deployOnLatest;

        public Descriptor() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, net.sf.json.JSONObject json) throws FormException {
            LOGGER.log(Level.ALL,"configure" + json.toString());
            req.bindJSON(this, json.getJSONObject("mdt-deploy"));

            save();

            return true;
        }

        @Override
        public String getDisplayName() {
            return "Deploy Artifacts to a MDT Server";
        }

        public FormValidation doCheckUrl(@QueryParameter String value) {
            if (value.length() == 0) {
                return FormValidation.error("URL must not be empty");
            }

            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                return FormValidation.error("URL must start with http:// or https://");
            }

            try {
                new URL(value).toURI();
            } catch (Exception e) {
                return FormValidation.error(e.getMessage());
            }

            return FormValidation.ok();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MdtPublishAction.class.getName());

}
