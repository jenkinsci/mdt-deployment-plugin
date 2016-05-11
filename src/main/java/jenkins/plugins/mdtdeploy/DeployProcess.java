package jenkins.plugins.mdtdeploy;

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.DependencyGraph;
import hudson.tasks.Publisher;
import hudson.model.Descriptor;
import hudson.util.DescribableList;
import hudson.model.ItemGroup;

/**
 * Created by rgroult on 09/05/16.
 */
public class DeployProcess extends AbstractProject<DeployProcess,Deploy> {
    private JobPropertyImpl jobProperty;
    private String mdtServer;

    protected Class<Deploy> getBuildClass() {
        return Deploy.class;
    }
/*
    DeployProcess(ItemGroup parent, String name) {
        super(parent, name);
    }*/

  /*  DeployProcess(JobPropertyImpl pp,String server){
        super()
        this.jobProperty = pp;
        this.mdtServer = server;
    }*/
/*
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

    }*/

    /*package*//*DeployProcess(JobPropertyImpl property, String name) {
        super(property, name);
    }*/

    /*package*/ DeployProcess(ItemGroup parent, String name) {
        super(parent, name);
    }


    @Override
    protected void buildDependencyGraph(DependencyGraph graph) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isFingerprintConfigured() {
        return false;
    }

    public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
        return null;
       /* TODO: extract from the buildsSteps field? Or should I separate builders and publishers?
        return new DescribableList<Publisher,Descriptor<Publisher>>(this);*/
    }
}
