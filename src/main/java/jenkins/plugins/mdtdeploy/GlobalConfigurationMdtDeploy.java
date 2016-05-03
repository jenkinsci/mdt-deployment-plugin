package jenkins.plugins.mdtdeploy;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Created by rgroult on 03/05/16.
 */
@Extension
public class GlobalConfigurationMdtDeploy extends GlobalConfiguration{
    private String url;
    public GlobalConfigurationMdtDeploy() {
        load();
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        req.bindJSON(this, json.getJSONObject("mdt-deploy"));
        save();
        return true;
    }

    public static GlobalConfigurationMdtDeploy get() {
        return GlobalConfigurationMdtDeploy.all().get(GlobalConfigurationMdtDeploy.class);
    }

    public String getUrl(){
        return url;
    }

    public void setUrl(String url){
        this.url = url;
    }
}
