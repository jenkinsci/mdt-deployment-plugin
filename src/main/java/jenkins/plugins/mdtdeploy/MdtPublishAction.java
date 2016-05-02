package jenkins.plugins.mdtdeploy;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
//import net.sf.json.JSONObject;


@SuppressWarnings("UnusedDeclaration") // This class will be loaded using its Descriptor.
public class MdtPublishAction extends Notifier {
    //public final AbstractBuild<?,?> owner;
    public boolean deployOnLatest;

    public boolean getDeployOnLatest(){
        return deployOnLatest;
    }
/*
    public MdtPublishAction(AbstractBuild<?,?> owner) {
        assert owner!=null;
        this.owner = owner;
    }*/

    @DataBoundConstructor
    public MdtPublishAction(boolean deployOnLatest) {
       // this(owner);
        this.deployOnLatest = deployOnLatest;
        LOGGER.log(Level.ALL,"MdtPublishAction");
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {
        Descriptor descriptor = getDescriptor();

        String mdtServerHostname = descriptor.url;
        boolean latest = deployOnLatest;
        String apiKey = null;
        String deployFile = null;

        JobPropertyImpl pp = (JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class);

        if (pp!=null){
            apiKey = pp.apiKey;
            deployFile = pp.deployFile;
        }

        if (mdtServerHostname == null || mdtServerHostname.length() == 0){
            listener.getLogger().println("MDT Deploy: MDT server not set, check your config !");
            return  true;
        }
        if (apiKey == null || apiKey.length() == 0){
            listener.getLogger().println("MDT Deploy: API key, check your config !");
            return  true;
        }
        if (deployFile == null || deployFile.length() == 0){
            listener.getLogger().println("MDT Deploy: Deployment json file, check your config !");
            return  true;
        }

        List<Run.Artifact> artifacts = build.getArtifacts();
        //sort artifact by path
        HashMap<String,Run.Artifact> sortedArtifact = new  HashMap<String,Run.Artifact>();
        for (Run.Artifact artifact :  artifacts) {
            sortedArtifact.put(artifact.relativePath,artifact);
        }

        //load deploy file
        Run.Artifact jsonFile = sortedArtifact.get(deployFile);
        if (jsonFile == null){
            listener.getLogger().println("MDT Deploy: Deployment json file not found ("+deployFile+")");
            return  true;
        }

        JSONParser parser = new JSONParser();
        try {
            Object obj = parser.parse(new FileReader(jsonFile.getFile()));
           // JSONArray jsonArray = (JSONArray) parser.parse(jsonFile.getFile());
            if (obj instanceof JSONArray){
                
            }
            JSONObject jsonObject = (JSONObject) obj;

        }catch (Exception e) {
            e.printStackTrace();
        }

        return true;


        //JSONObject jsonDeploy = JSONObject.fromObject(jsonFile.getFile());

/*
        try {
            MultipartBuilder multipart = new MultipartBuilder();
            multipart.type(MultipartBuilder.FORM);
            for (Run.Artifact artifact : artifacts) {
                multipart.addFormDataPart(artifact.getFileName(), artifact.getFileName(),
                        RequestBody.create(null, artifact.getFile()));
            }

            OkHttpClient client = new OkHttpClient();
            client.setConnectTimeout(30, TimeUnit.SECONDS);
            client.setReadTimeout(60, TimeUnit.SECONDS);

            Request.Builder builder = new Request.Builder();
            builder.url(url);
            builder.header("Job-Name", build.getProject().getName());
            builder.header("Build-Number", String.valueOf(build.getNumber()));
            builder.header("Build-Timestamp", String.valueOf(build.getTimeInMillis()));
            if (headers != null && headers.length() > 0) {
                String[] lines = headers.split("\r?\n");
                for (String line : lines) {
                    int index = line.indexOf(':');
                    builder.header(line.substring(0, index).trim(), line.substring(index + 1).trim());
                }
            }
            builder.post(multipart.build());

            Request request = builder.build();
            listener.getLogger().println(String.format("---> POST %s", url));
            listener.getLogger().println(request.headers());

            long start = System.nanoTime();
            Response response = client.newCall(request).execute();
            long time = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            listener.getLogger()
                    .println(String.format("<--- %s %s (%sms)", response.code(), response.message(), time));
            listener.getLogger().println(response.body().string());
        } catch (Exception e) {
            e.printStackTrace(listener.getLogger());
        }

*/

        listener.getLogger().println("MDT server : "+descriptor.url);
        listener.getLogger().println("latest  : "+deployOnLatest);


        listener.getLogger().println("api key  : "+pp.apiKey);
        listener.getLogger().println("deployFile  : "+pp.deployFile);

        listener.getLogger().println("MDT Deploy: Skipping because of Not implemented");
        return true;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
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

    private static final Logger LOGGER = Logger.getLogger(JobPropertyImpl.class.getName());

}
