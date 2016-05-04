package jenkins.plugins.mdtdeploy;

import hudson.model.*;
import java.io.StringWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.util.ByteArrayOutputStream2;
import hudson.util.StreamTaskListener;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

/**
 * Created by rgroult on 02/05/16.
 */
public class MdtBuildAction implements Action {

    private String message;
    private String mdtServer;
    private AbstractBuild<?, ?> build;
    private String lastDeployLog;

    @Override
    public String getIconFileName() {
        return "logo_mdt.png";
    }

    @Override
    public String getDisplayName() {
        return "Deploy on MDT ...";
    }

    @Override
    public String getUrlName() {
        return "MdtBuildAction";
    }

    public String getMdtServer(){
        return mdtServer;
    }
    public String getMessage() {
        return this.message;
    }

    public int getBuildNumber() {
        return this.build.number;
    }

    public String getLastDeployLog(){
        return  lastDeployLog;
        /*
        if (listener != null) {
            String log = listener.getLogger().toString();
            if (log != null){
                return log;
            }
        }
        return "";*/
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public HttpResponse doDeploy() throws IOException {
        LOGGER.log(Level.ALL,"Deploy started ..");

        GlobalConfigurationMdtDeploy globalConfig = GlobalConfigurationMdtDeploy.get();
        JobPropertyImpl pp = (JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class);

        List<Run<?,?>.Artifact> artifacts = (List<Run<? , ?>.Artifact>) build.getArtifacts();

        MdtPublishAction publishAction = new MdtPublishAction(false);// (MdtPublishAction) build.getProject().getActions(MdtPublishAction.class);

        StreamTaskListener listener = null;
        StringWriter writer = new StringWriter();
        try {
            listener = new StreamTaskListener(writer);
        }catch(IOException e){
            listener = StreamTaskListener.fromStdout();
        }

        publishAction.performDeploy(mdtServer,pp.apiKey,pp.deployFile,artifacts,listener);
        listener.getLogger().flush();
        lastDeployLog = writer.getBuffer().toString();

        //publishAction.deployOnLatest = false;
        //publishAction.perform2(build,null,StreamTaskListener.fromStdout());


        //List<?> artifacts = build.getArtifacts();
        return HttpResponses.redirectToDot();


    }

    MdtBuildAction(final AbstractBuild<?, ?> build)
    {
        this.build = build;
        GlobalConfigurationMdtDeploy globalConfig = GlobalConfigurationMdtDeploy.get();
        this.mdtServer = globalConfig.getUrl();

    }
    private static final Logger LOGGER = Logger.getLogger(MdtBuildAction.class.getName());
}
