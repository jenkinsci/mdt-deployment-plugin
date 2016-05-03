package jenkins.plugins.mdtdeploy;

import hudson.model.*;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;

/**
 * Created by rgroult on 02/05/16.
 */
public class MdtBuildAction implements Action {

    private String message;
    private String mdtServer;
    private AbstractBuild<?, ?> build;

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

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

    public HttpResponse doDeploy() throws IOException {
        LOGGER.log(Level.ALL,"Deploy started ..");

        GlobalConfigurationMdtDeploy globalConfig = GlobalConfigurationMdtDeploy.get();
       /* MdtPublishAction publishAction = (MdtPublishAction) build.getProject().getActions(MdtPublishAction.class);

        publishAction.deployOnLatest = false;
        publishAction.perform(build,null,listener);*/

        JobPropertyImpl pp = (JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class);
        List<?> artifacts = build.getArtifacts();
        return HttpResponses.redirectToDot();
    }

    MdtBuildAction(final AbstractBuild<?, ?> build)
    {
        this.build = build;
    }
    private static final Logger LOGGER = Logger.getLogger(MdtBuildAction.class.getName());
}
