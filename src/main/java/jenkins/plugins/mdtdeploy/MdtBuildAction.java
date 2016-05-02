package jenkins.plugins.mdtdeploy;
import hudson.tasks.Builder;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.model.AbstractProject;
import hudson.Extension;
import hudson.tasks.BuildStepDescriptor;

/**
 * Created by rgroult on 02/05/16.
 */
public class MdtBuildAction  extends Builder{
    private final String task;

    @DataBoundConstructor
    public MdtBuildAction(String task) {
        this.task = task;
    }

    public String getTask() {
        return "toto";
    }

    @Extension
    public static class Descriptor extends BuildStepDescriptor<Builder> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true; //FreeStyleProject.class.isAssignableFrom(jobType);
        }

        @Override
        public String getDisplayName() {
            return "execute test task";
        }
    }
}
