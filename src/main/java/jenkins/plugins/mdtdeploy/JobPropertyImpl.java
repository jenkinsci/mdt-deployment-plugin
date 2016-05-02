package jenkins.plugins.mdtdeploy;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.Ancestor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Failure;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.ItemGroupMixIn;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.listeners.ItemListener;
import hudson.remoting.Callable;
import hudson.util.IOUtils;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;


public final class JobPropertyImpl extends JobProperty<AbstractProject<?,?>> {
    public String apiKey;
    public String deployFile;

    public boolean getEnable() { return (apiKey!=null || deployFile!=null);}

    @DataBoundConstructor
    public JobPropertyImpl() {
        LOGGER.log(Level.ALL,"JobPropertyImpl");
    }

    private JobPropertyImpl(StaplerRequest req, JSONObject json) throws Descriptor.FormException, IOException {
        LOGGER.log(Level.ALL,"JobPropertyImpl");
        if(json.has("mdtInfos"))
            json = json.getJSONObject("mdtInfos");
        try {
            apiKey = json.getString("apiKey");
            deployFile = json.getString("deployFile");
        } catch (Failure f) {
            throw new Descriptor.FormException(f.getMessage(),"");
        }
    }

    @Extension
    public static final class DescriptorImpl extends JobPropertyDescriptor {
        public DescriptorImpl() {
            super();
        }

        public DescriptorImpl(Class<? extends JobProperty<?>> clazz) {
            super(clazz);
        }

        public String getDisplayName() {
            return "MDT Deployment";
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return AbstractProject.class.isAssignableFrom(jobType);
        }

        @Override
        public JobPropertyImpl newInstance(StaplerRequest req, JSONObject json) throws Descriptor.FormException {
            try {
                LOGGER.log(Level.INFO,json.toString());
                if(json.has("mdtInfos"))
                    return new JobPropertyImpl(req, json);
                return null;
            } catch (IOException e) {
                throw new FormException("Failed to create",e,null); // TODO:hmm
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(JobPropertyImpl.class.getName());
}