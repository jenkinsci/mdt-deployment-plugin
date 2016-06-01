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
    public enum Status {
        NEW,
        SUCCESS,
        FAILED
    }

    //private String mdtServer;
    /*private String apiKey;
    private String deployArtifactFilename;*/
    private AbstractBuild<?, ?> build;
    private FutureTask<Boolean> currentDeployTask;
    private StringWriter logWriter;
    transient private TaskListener actionListener = null ;
    private Status status;

    MdtBuildAction(final AbstractBuild<?, ?> build)
    {
        this.build = build;
       /* GlobalConfigurationMdtDeploy globalConfig = GlobalConfigurationMdtDeploy.get();
        this.mdtServer = globalConfig.getUrl();
        JobPropertyImpl pp = (JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class);
        this.apiKey = pp.apiKey;
        this.deployArtifactFilename = pp.deployFile;*/
        this.logWriter =  new StringWriter();
        this.status = Status.NEW;
    }

    public boolean hasDeployed(){
        return status!=Status.NEW;
    }

    public String getBadgeIconFileName() {
        updateStatusIfNeeded();
        switch (status){
            case SUCCESS:
                return "/plugin/mdt-deployment/images/16x16/logo_mdt_success.png";
            case FAILED:
                return "/plugin/mdt-deployment/images/16x16/logo_mdt_failed.png";
            default:
                return "/plugin/mdt-deployment/images/16x16/logo_mdt.png";
        }
    }

    @Override
    public String getIconFileName() {
        updateStatusIfNeeded();
        switch (status){
            case SUCCESS:
                return "/plugin/mdt-deployment/images/logo_mdt_success.png";
            case FAILED:
                return "/plugin/mdt-deployment/images/logo_mdt_failed.png";
            default:
                return "/plugin/mdt-deployment/images/logo_mdt.png";
        }
    }

    private void updateStatusIfNeeded(){
        if (this.currentDeployTask != null && this.currentDeployTask.isDone()){
            try {
                if (currentDeployTask.get()) {
                    status = Status.SUCCESS;
                } else {
                    status = Status.FAILED;
                }
                this.currentDeployTask = null;
            }catch (Exception e){
                status = Status.FAILED;
            }
        }
    }

    private void deployEnded(boolean isSucess){
        if (isSucess) {
            status = Status.SUCCESS;
        } else {
            status = Status.FAILED;
        }
    }
    public boolean isLogUpdated(){
        return getIsDeploying();
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
        GlobalConfigurationMdtDeploy globalConfig = GlobalConfigurationMdtDeploy.get();
        return globalConfig.getUrl();
    }

    public int getBuildNumber() {
        return this.build.number;
    }

    public Status getStatus(){
        return status;
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

    public void doProgressiveLog(StaplerRequest req, StaplerResponse rsp) throws IOException{
        if (actionListener != null ){
            actionListener.getLogger().flush();
        }
        rsp.setContentType("text/plain");
        rsp.addHeader("X-More-Data", ""+getIsDeploying());

        int start = 0;
        String s = req.getParameter("start");
        //LOGGER.log(Level.ALL,"params:"+req.getParameterMap());
        if(s != null) {
            try{
                start = Integer.parseInt(s);
            }catch(Exception e){}
            //LOGGER.log(Level.ALL,"start .. "+start+ " s:"+s);
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
        status = Status.NEW;
        ExecutorService executor = Executors.newFixedThreadPool(1);
        this.currentDeployTask = new FutureTask<Boolean>(new DeployTask(build,actionListener,this));
        actionListener.getLogger().println("<b>Starting Deploy ...</b>");
        executor.execute(this.currentDeployTask);

        return HttpResponses.redirectToDot();
    }




    public void writeLogTo(XMLOutput out) throws IOException {

        out.asWriter().write(getLastDeployLog());
    }


    private static final Logger LOGGER = Logger.getLogger(MdtBuildAction.class.getName());

    static public final class  DeployTask implements  Callable<Boolean> {
       /* String mdtServer;
        String apiKey;
        String deployArtifactFilename;*/
        AbstractBuild<?, ?> build;
        TaskListener actionListener;
        List<Run<?,?>.Artifact> artifacts;
        MdtBuildAction parent;
        private MdtPublishAction publishAction;

        public DeployTask(AbstractBuild<?, ?> build,TaskListener listener,MdtBuildAction parent){
           /* this.build = build;
            this.mdtServer = mdtServer;*/
            this.actionListener = listener;
            this.parent = parent;
          /*  this.apiKey = apiKey;
            this.deployArtifactFilename = deployArtifactFilename;*/
            artifacts = (List<Run<? , ?>.Artifact>) build.getArtifacts();
            publishAction = new MdtPublishAction(false);
            publishAction.configureDeployInfos((JobPropertyImpl) build.getProject().getProperty(JobPropertyImpl.class));
        }

        public Boolean call() {
           // final MdtPublishAction publishAction = new MdtPublishAction(false);
            //LOGGER.log(Level.SEVERE,"start Call");
            Boolean result =  publishAction.performDeploy(artifacts,actionListener);
            //LOGGER.log(Level.SEVERE,"End Call");
            actionListener.getLogger().flush();
            if (result){
                actionListener.getLogger().println("<b>Deployment done successfully<b/>");
            }else {
                actionListener.getLogger().println("<b>Deployment Failed !<b/>");
            }
            parent.deployEnded(result);
            return result;
        }
    }

}
