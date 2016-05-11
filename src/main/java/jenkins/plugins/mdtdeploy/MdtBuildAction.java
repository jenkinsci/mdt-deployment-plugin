package jenkins.plugins.mdtdeploy;

import hudson.Launcher;
import hudson.model.*;

import hudson.model.LargeText;

import java.io.*;
import java.nio.charset.Charset;
import java.util.AbstractQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.*;

import hudson.model.queue.AbstractQueueTask;
import hudson.model.queue.SubTask;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.ByteArrayOutputStream2;
import hudson.util.DescribableList;
import hudson.util.StreamTaskListener;

import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.commons.jelly.tags.xml.ElementTag;
import org.kohsuke.args4j.spi.BooleanOptionHandler;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import hudson.model.Cause.UserCause;

import javax.xml.ws.http.HTTPBinding;
import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 * Created by rgroult on 02/05/16.
 */
public class MdtBuildAction implements Action {

    private String message;
    private String mdtServer;
    private AbstractBuild<?, ?> build;
    private String lastDeployLog;
    private FutureTask<Boolean> currentDeployTask;
    private StringWriter logWriter = new StringWriter();
    transient private TaskListener actionListener = null ;

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

    public boolean getIsDeploying(){
        boolean result = false;
        if (this.currentDeployTask != null){
            result = !this.currentDeployTask.isDone();
        }
        //LOGGER.log(Level.SEVERE,"getIsDeploying .."+ result);
        return result;
    }
    public String getLastDeployLog(){

        String log = logWriter.toString();
        if (log != null){
            log = log.replaceAll("\n","<br/>");
        }else {
            log= "";
        }

        return  log;
    }

    public AbstractBuild<?, ?> getBuild() {
        return build;
    }

/*
    private Future<Boolean> deploy(MdtPublishAction action,String server,String apiKey,String deployFile,TaskListener listener){
        List<Run<?,?>.Artifact> artifacts = (List<Run<? , ?>.Artifact>) build.getArtifacts();
        return new AsyncResult<>(action.performDeploy(mdtServer,apiKey,deployFile,artifacts,listener));
    }
*/
    public void doProgressiveLog(StaplerRequest req, StaplerResponse rsp) throws IOException{
        //LOGGER.log(Level.SEVERE,"doProgressiveLog ..");
        if (actionListener != null ){
            actionListener.getLogger().flush();
        }
        rsp.setContentType("text/plain");
        rsp.addHeader("X-More-Data", ""+getIsDeploying());

        int start = 0;
        String s = req.getParameter("start");
        LOGGER.log(Level.SEVERE,"params:"+req.getParameterMap());
        if(s != null) {
            try{
                start = Integer.parseInt(s);
            }catch(Exception e){}
            LOGGER.log(Level.SEVERE,"start .. "+start+ " s:"+s);
        }
        String currentLog = getLastDeployLog();
        int length = currentLog.length();
        rsp.addHeader("X-Text-Size", length+"");
        if (start < currentLog.length()){
            currentLog= currentLog.substring(start);
            PrintWriter w = rsp.getWriter();
            w.write(currentLog);

        }
    }

    public HttpResponse doDeploy() throws IOException {
        //LOGGER.log(Level.SEVERE,"Deploy started ..");

        if (getIsDeploying()){
            return HttpResponses.redirectToDot();
        }

        try {
            actionListener = new StreamTaskListener(this.logWriter);
        }catch(IOException e){
            actionListener = StreamTaskListener.fromStdout();
        }

        logWriter.getBuffer().setLength(0);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        this.currentDeployTask = new FutureTask<Boolean>(new DeployTask(mdtServer,build,actionListener));
        actionListener.getLogger().println("Starting Deploy ...");
        executor.execute(this.currentDeployTask);

        return HttpResponses.redirectToDot();
    }

    MdtBuildAction(final AbstractBuild<?, ?> build)
    {
        this.build = build;
        GlobalConfigurationMdtDeploy globalConfig = GlobalConfigurationMdtDeploy.get();
        this.mdtServer = globalConfig.getUrl();
    }

    public boolean isLogUpdated(){
        return getIsDeploying();
    }

    public void writeLogTo(XMLOutput out) throws IOException {

        out.asWriter().write(getLastDeployLog());
    }


    private static final Logger LOGGER = Logger.getLogger(MdtBuildAction.class.getName());

    static public final class  DeployTask implements  Callable<Boolean> {
        String mdtServer;
        AbstractBuild<?, ?> build;
        TaskListener actionListener;
        JobPropertyImpl jobProperty;
        List<Run<?,?>.Artifact> artifacts;

        public DeployTask(String mdtServer,AbstractBuild<?, ?> build,TaskListener listener ){
            this.build = build;
            this.mdtServer = mdtServer;
            this.actionListener = listener;
            jobProperty = (JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class);
            artifacts = (List<Run<? , ?>.Artifact>) build.getArtifacts();
        }

        public Boolean call() {
            final MdtPublishAction publishAction = new MdtPublishAction(false);
            LOGGER.log(Level.SEVERE,"start Call");
            Boolean result =  publishAction.performDeploy(mdtServer,jobProperty.apiKey,jobProperty.deployFile,artifacts,actionListener);
            LOGGER.log(Level.SEVERE,"End Call");
            actionListener.getLogger().flush();
            return result;
        }


/*
            JobPropertyImpl jobProperty = (JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class);
            List<Run<?,?>.Artifact> artifacts = (List<Run<? , ?>.Artifact>) build.getArtifacts();
            TaskListener actionListener =listener ;*/

    }

}
