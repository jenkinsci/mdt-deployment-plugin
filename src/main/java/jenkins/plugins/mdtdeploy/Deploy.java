package jenkins.plugins.mdtdeploy;

import hudson.model.AbstractBuild;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by rgroult on 09/05/16.
 */
public class Deploy extends AbstractBuild<DeployProcess,Deploy> {

    public Deploy(DeployProcess job) throws IOException {
        super(job);
    }

    public void run() {
        LOGGER.log(Level.ALL,"Run'");
    }

    private static final Logger LOGGER = Logger.getLogger(Deploy.class.getName());
}
