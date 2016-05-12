package jenkins.plugins.mdtdeploy;

import com.squareup.okhttp.*;
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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import java.nio.file.Paths;


@SuppressWarnings("UnusedDeclaration") // This class will be loaded using its Descriptor.
public class MdtPublishAction extends Notifier {
    //public final AbstractBuild<?,?> owner;
    public boolean deployOnLatest;

    public boolean getDeployOnLatest(){
        return deployOnLatest;
    }

    @DataBoundConstructor
    public MdtPublishAction(boolean deployOnLatest) {
       // this(owner);
        this.deployOnLatest = deployOnLatest;
        LOGGER.log(Level.ALL,"MdtPublishAction");
    }

    public boolean performDeploy(String mdtServerHostname,String apiKey,String deployFilename,List<Run<? , ?>.Artifact> artifacts,TaskListener listener){
        if (mdtServerHostname == null || mdtServerHostname.length() == 0){
            listener.getLogger().println("MDT Deploy: MDT server not set, check your config !");
            return  false;
        }
        if (apiKey == null || apiKey.length() == 0){
            listener.getLogger().println("MDT Deploy: API key, check your config !");
            return  false;
        }
        if (deployFilename == null || deployFilename.length() == 0){
            listener.getLogger().println("MDT Deploy: Deployment json file, check your config !");
            return  false;
        }

        boolean latest = deployOnLatest;

        //sort artifact by path
        HashMap<String,Run.Artifact> sortedArtifact = new HashMap<String,Run.Artifact>();
        for (Run.Artifact artifact :  artifacts) {
            sortedArtifact.put(artifact.relativePath,artifact);
        }

        //load deploy file
        Run.Artifact jsonFile = sortedArtifact.get(deployFilename);
        if (jsonFile == null){
            listener.getLogger().println("MDT Deploy: Deployment json file not found ("+deployFilename+")");
            return  true;
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
                Run.Artifact artifact = findArtifact(jsonFile,jsonObject.get("file").toString(),sortedArtifact);
                if (artifact !=null){
                    if(!deleteArtifact(artifact,jsonObject,apiKey,mdtServerHostname,latest,listener)){
                        listener.getLogger().println("Error deploying artifact "+jsonObject.get("file")+". Aborting!");
                        return false;
                    }

                    if(!sendArtifact(artifact,jsonObject,apiKey,mdtServerHostname,latest,listener)){
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
       /* Descriptor descriptor = getDescriptor();

        String mdtServerHostname = descriptor.url;*/
        boolean latest = deployOnLatest;
        String apiKey = null;
        String deployFile = null;

        JobPropertyImpl pp = (JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class);

        if (pp!=null){
            apiKey = pp.apiKey;
            deployFile = pp.deployFile;
        }
        GlobalConfigurationMdtDeploy globalConfig = GlobalConfigurationMdtDeploy.get();
        String mdtServerHostname = globalConfig.getUrl();

        return performDeploy(mdtServerHostname,apiKey,deployFile,build.getArtifacts(),listener);
    }

    private Run.Artifact findArtifact(Run.Artifact deployFileArtifact, String relativeNameFromDeployFile,HashMap<String,Run.Artifact> sortedArtifacts){
        LOGGER.log(Level.ALL,"find artifact "+relativeNameFromDeployFile);
        //compute artifact 'full' relative path
        String relativePath = Paths.get(deployFileArtifact.relativePath).resolveSibling(relativeNameFromDeployFile).toString();
        return  sortedArtifacts.get(relativePath);
    }

    private boolean deleteArtifact(Run.Artifact artifact,JSONObject jsonInfo,String apiKey,String mdtServer,boolean latest,TaskListener listener){
        try {
            String url = mdtServer;
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

    private boolean sendArtifact(Run.Artifact artifact,JSONObject jsonInfo,String apiKey,String mdtServer,boolean latest,TaskListener listener){
        try {
            String url = mdtServer;
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
        client.setConnectTimeout(30, TimeUnit.SECONDS);
        client.setReadTimeout(60, TimeUnit.SECONDS);

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
