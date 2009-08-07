/*
 * Copyright 1999-2001,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.startup;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.StringManager;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Startup event listener for a <b>Context</b> that configures the properties
 * of that Context, and the associated defined servlets.
 *
 * @author Craig R. McClanahan
 * @author Jean-Francois Arcand
 * @version $Revision$ $Date$
 */

public final class ContextConfig
    implements LifecycleListener {

    protected static org.apache.commons.logging.Log log=
        org.apache.commons.logging.LogFactory.getLog( ContextConfig.class );

    // ----------------------------------------------------- Instance Variables

    /**
     * The set of Authenticators that we know how to configure.  The key is
     * the name of the implemented authentication method, and the value is
     * the fully qualified Java class name of the corresponding Valve.
     */
    protected static Properties authenticators = null;


    /**
     * The Context we are associated with.
     */
    protected Context context = null;


    /**
     * The default web application's deployment descriptor location.
     */
    protected String defaultWebXml = null;
    
    
    /**
     * Track any fatal errors during startup configuration processing.
     */
    protected boolean ok = false;


    /**
     * Any parse error which occurred while parsing XML descriptors.
     */
    protected SAXParseException parseException = null;

    
    /**
     * Original docBase.
     */
    protected String originalDocBase = null;
    

    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The <code>Digester</code> we will use to process web application
     * context files.
     */
    protected static Digester contextDigester = null;
    
    
    /**
     * The <code>Digester</code> we will use to process web application
     * deployment descriptor files.
     */
    protected static Digester webDigester = null;
    
    
    /**
     * The <code>Rule</code> used to parse the web.xml
     */
    protected static WebRuleSet webRuleSet = new WebRuleSet();

    /**
     * Attribute value used to turn on/off XML validation
     */
     protected static boolean xmlValidation = false;


    /**
     * Attribute value used to turn on/off XML namespace awarenes.
     */
    protected static boolean xmlNamespaceAware = false;

    
    /**
     * Deployment count.
     */
    protected static long deploymentCount = 0L;
    
    
    // ------------------------------------------------------------- Properties


    /**
     * Return the location of the default deployment descriptor
     */
    public String getDefaultWebXml() {
        if( defaultWebXml == null ) defaultWebXml=Constants.DefaultWebXml;
        return (this.defaultWebXml);

    }


    /**
     * Set the location of the default deployment descriptor
     *
     * @param path Absolute/relative path to the default web.xml
     */
    public void setDefaultWebXml(String path) {

        this.defaultWebXml = path;

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Process events for an associated Context.
     *
     * @param event The lifecycle event that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event) {

        // Identify the context we are associated with
        try {
            context = (Context) event.getLifecycle();
        } catch (ClassCastException e) {
            log.error(sm.getString("contextConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.START_EVENT)) {
            start();
        } else if (event.getType().equals(StandardContext.BEFORE_START_EVENT)) {
            beforeStart();
        } else if (event.getType().equals(Lifecycle.STOP_EVENT)) {
            stop();
        } else if (event.getType().equals(Lifecycle.INIT_EVENT)) {
            init();
        } else if (event.getType().equals(Lifecycle.DESTROY_EVENT)) {
            destroy();
        }

    }


    // -------------------------------------------------------- protected Methods


    /**
     * Process the application configuration file, if it exists.
     */
    protected void applicationWebConfig() {

        // Open the application web.xml file, if it exists
        InputStream stream = null;
        ServletContext servletContext = context.getServletContext();
        if (servletContext != null)
            stream = servletContext.getResourceAsStream
                (Constants.ApplicationWebXml);
        if (stream == null) {
            log.info(sm.getString("contextConfig.applicationMissing") + " " + context);
            return;
        }
        
        long t1=System.currentTimeMillis();

        if (webDigester == null){
            webDigester = createWebDigester();
        }
        
        URL url=null;
        // Process the application web.xml file
        synchronized (webDigester) {
            try {
                url =
                    servletContext.getResource(Constants.ApplicationWebXml);
                if( url!=null ) {
                    InputSource is = new InputSource(url.toExternalForm());
                    is.setByteStream(stream);
                    webDigester.clear();
                    if (context instanceof StandardContext) {
                        ((StandardContext) context).setReplaceWelcomeFiles(true);
                    }
                    webDigester.push(context);
                    webDigester.setErrorHandler(new ContextErrorHandler());
                    webDigester.parse(is);
                    if (parseException != null) {
                        ok = false;
                    }
                } else {
                    log.info("No web.xml, using defaults " + context );
                }
            } catch (SAXParseException e) {
                log.error(sm.getString("contextConfig.applicationParse"), e);
                log.error(sm.getString("contextConfig.applicationPosition",
                                 "" + e.getLineNumber(),
                                 "" + e.getColumnNumber()));
                ok = false;
            } catch (Exception e) {
                log.error(sm.getString("contextConfig.applicationParse"), e);
                ok = false;
            } finally {
                parseException = null;
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException e) {
                    log.error(sm.getString("contextConfig.applicationClose"), e);
                }
                webDigester.push(null);
            }
        }
        webRuleSet.recycle();

        long t2=System.currentTimeMillis();
        if (context instanceof StandardContext) {
            ((StandardContext) context).setStartupTime(t2-t1);
        }
    }


    /**
     * Set up a manager.
     */
    protected synchronized void managerConfig() {

        if (context.getManager() == null) {
            if ((context.getCluster() != null) && context.getDistributable()) {
                try {
                    context.setManager(context.getCluster().createManager
                                       (context.getName()));
                } catch (Exception ex) {
                    log.error("contextConfig.clusteringInit", ex);
                    ok = false;
                }
            } else {
                context.setManager(new StandardManager());
            }
        }

    }


    /**
     * Set up an Authenticator automatically if required, and one has not
     * already been configured.
     */
    protected synchronized void authenticatorConfig() {

        // Does this Context require an Authenticator?
        SecurityConstraint constraints[] = context.findConstraints();
        if ((constraints == null) || (constraints.length == 0))
            return;
        LoginConfig loginConfig = context.getLoginConfig();
        if (loginConfig == null) {
            loginConfig = new LoginConfig("NONE", null, null, null);
            context.setLoginConfig(loginConfig);
        }

        // Has an authenticator been configured already?
        if (context instanceof Authenticator)
            return;
        if (context instanceof ContainerBase) {
            Pipeline pipeline = ((ContainerBase) context).getPipeline();
            if (pipeline != null) {
                Valve basic = pipeline.getBasic();
                if ((basic != null) && (basic instanceof Authenticator))
                    return;
                Valve valves[] = pipeline.getValves();
                for (int i = 0; i < valves.length; i++) {
                    if (valves[i] instanceof Authenticator)
                        return;
                }
            }
        } else {
            return;     // Cannot install a Valve even if it would be needed
        }

        // Has a Realm been configured for us to authenticate against?
        if (context.getRealm() == null) {
            log.error(sm.getString("contextConfig.missingRealm"));
            ok = false;
            return;
        }

        // Load our mapping properties if necessary
        if (authenticators == null) {
            try {
                InputStream is=this.getClass().getClassLoader().getResourceAsStream("org/apache/catalina/startup/Authenticators.properties");
                if( is!=null ) {
                    authenticators = new Properties();
                    authenticators.load(is);
                } else {
                    log.error(sm.getString("contextConfig.authenticatorResources"));
                    ok=false;
                    return;
                }
            } catch (IOException e) {
                log.error(sm.getString("contextConfig.authenticatorResources"), e);
                ok = false;
                return;
            }
        }

        // Identify the class name of the Valve we should configure
        String authenticatorName = null;
        authenticatorName =
                authenticators.getProperty(loginConfig.getAuthMethod());
        if (authenticatorName == null) {
            log.error(sm.getString("contextConfig.authenticatorMissing",
                             loginConfig.getAuthMethod()));
            ok = false;
            return;
        }

        // Instantiate and install an Authenticator of the requested class
        Valve authenticator = null;
        try {
            Class authenticatorClass = Class.forName(authenticatorName);
            authenticator = (Valve) authenticatorClass.newInstance();
            if (context instanceof ContainerBase) {
                Pipeline pipeline = ((ContainerBase) context).getPipeline();
                if (pipeline != null) {
                    ((ContainerBase) context).addValve(authenticator);
                    if (log.isDebugEnabled())
                        log.debug(sm.getString("contextConfig.authenticatorConfigured",
                                     loginConfig.getAuthMethod()));
                }
            }
        } catch (Throwable t) {
            log.error(sm.getString("contextConfig.authenticatorInstantiate",
                             authenticatorName), t);
            ok = false;
        }

    }


    /**
     * Create (if necessary) and return a Digester configured to process the
     * web application deployment descriptor (web.xml).
     */
    protected static Digester createWebDigester() {
        Digester webDigester =
            createWebXmlDigester(xmlNamespaceAware, xmlValidation);
        return webDigester;
    }


    /**
     * Create (if necessary) and return a Digester configured to process the
     * web application deployment descriptor (web.xml).
     */
    public static Digester createWebXmlDigester(boolean namespaceAware,
                                                boolean validation) {
        
        Digester webDigester =  DigesterFactory.newDigester(xmlValidation,
                                                            xmlNamespaceAware,
                                                            webRuleSet);
        return webDigester;
    }

    
    /**
     * Create (if necessary) and return a Digester configured to process the
     * context configuration descriptor for an application.
     */
    protected Digester createContextDigester() {
        Digester digester = new Digester();
        digester.setValidating(false);
        RuleSet contextRuleSet = new ContextRuleSet("", false);
        digester.addRuleSet(contextRuleSet);
        RuleSet namingRuleSet = new NamingRuleSet("Context/");
        digester.addRuleSet(namingRuleSet);
        return digester;
    }


    protected String getBaseDir() {
        Container engineC=context.getParent().getParent();
        if( engineC instanceof StandardEngine ) {
            return ((StandardEngine)engineC).getBaseDir();
        }
        return System.getProperty("catalina.base");
    }

    /**
     * Process the default configuration file, if it exists.
     * The default config must be read with the container loader - so
     * container servlets can be loaded
     */
    protected void defaultWebConfig() {
        long t1=System.currentTimeMillis();

        // Open the default web.xml file, if it exists
        if( defaultWebXml==null && context instanceof StandardContext ) {
            defaultWebXml=((StandardContext)context).getDefaultWebXml();
        }
        // set the default if we don't have any overrides
        if( defaultWebXml==null ) getDefaultWebXml();

        File file = new File(this.defaultWebXml);
        if (!file.isAbsolute()) {
            file = new File(getBaseDir(),
                            this.defaultWebXml);
        }

        InputStream stream = null;
        InputSource source = null;

        try {
            if ( ! file.exists() ) {
                // Use getResource and getResourceAsStream
                stream = getClass().getClassLoader()
                    .getResourceAsStream(defaultWebXml);
                if( stream != null ) {
                    source = new InputSource
                            (getClass().getClassLoader()
                            .getResource(defaultWebXml).toString());
                } else {
                    log.info("No default web.xml");
                }
            } else {
                source =
                    new InputSource("file://" + file.getAbsolutePath());
                stream = new FileInputStream(file);
            }
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.defaultMissing") 
                      + " " + defaultWebXml + " " + file , e);
        }

        if (webDigester == null){
            webDigester = createWebDigester();
        }
        
        if (stream != null) {
            processDefaultWebConfig(webDigester, stream, source);
            webRuleSet.recycle();
        }

        long t2=System.currentTimeMillis();
        if( (t2-t1) > 200 )
            log.debug("Processed default web.xml " + file + " "  + ( t2-t1));

        stream = null;
        source = null;

        file = new File(getConfigBase(), "web.xml.default");
        
        try {
           if (file.exists()) {
                source =
                    new InputSource("file://" + file.getAbsolutePath());
                stream = new FileInputStream(file);
            }
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.defaultMissing") 
                      + " " + file , e);
        }
        
        if (stream != null) {
            processDefaultWebConfig(webDigester, stream, source);
            webRuleSet.recycle();
        }

    }


    /**
     * Process a default web.xml.
     */
    protected void processDefaultWebConfig(Digester digester, InputStream stream, 
            InputSource source) {
        // Process the default web.xml file
        synchronized (digester) {
            try {
                source.setByteStream(stream);
                
                if (context instanceof StandardContext)
                    ((StandardContext) context).setReplaceWelcomeFiles(true);
                digester.clear();
                digester.setClassLoader(this.getClass().getClassLoader());
                //log.info( "Using cl: " + webDigester.getClassLoader());
                digester.setUseContextClassLoader(false);
                digester.push(context);
                digester.setErrorHandler(new ContextErrorHandler());
                digester.parse(source);
                if (parseException != null) {
                    ok = false;
                }
            } catch (SAXParseException e) {
                log.error(sm.getString("contextConfig.defaultParse"), e);
                log.error(sm.getString("contextConfig.defaultPosition",
                                 "" + e.getLineNumber(),
                                 "" + e.getColumnNumber()));
                ok = false;
            } catch (Exception e) {
                log.error(sm.getString("contextConfig.defaultParse"), e);
                ok = false;
            } finally {
                parseException = null;
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException e) {
                    log.error(sm.getString("contextConfig.defaultClose"), e);
                }
            }
        }
    }


    /**
     * Process the default configuration file, if it exists.
     */
    protected void contextConfig() {
        
        // FIXME: Externalize default context.xml path the same way as web.xml
        if (!context.getOverride()) {
            processContextConfig(new File(getBaseDir(), "conf/context.xml"));
            processContextConfig(new File(getConfigBase(), "context.xml.default"));
        }
        if (context.getConfigFile() != null)
            processContextConfig(new File(context.getConfigFile()));
        
    }

    
    /**
     * Process a context.xml.
     */
    protected void processContextConfig(File file) {
        
        if (log.isDebugEnabled())
            log.debug("Processing context [" + context.getName() + "] configuration file " + file);
        
        // Add as watched resource so that cascade reload occurs if a default
        // config file is modified/added/removed
        context.addWatchedResource(file.getAbsolutePath());

        InputSource source = null;
        InputStream stream = null;
        try {
            if (file.exists()) {
                stream = new FileInputStream(file);
                source =
                    new InputSource("file://" + file.getAbsolutePath());
            } else if (log.isDebugEnabled()) {
                log.debug("Context [" + context.getName() + "] configuration file " + file + " not found");
            }
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.defaultMissing") + file);
        }
        if (source == null)
            return;
        if (contextDigester == null){
            contextDigester = createContextDigester();
        }
        synchronized (contextDigester) {
            try {
                source.setByteStream(stream);
                contextDigester.clear();
                contextDigester.setClassLoader(this.getClass().getClassLoader());
                //log.info( "Using cl: " + webDigester.getClassLoader());
                contextDigester.setUseContextClassLoader(false);
                contextDigester.push(context.getParent());
                contextDigester.push(context);
                contextDigester.setErrorHandler(new ContextErrorHandler());
                contextDigester.parse(source);
                if (parseException != null) {
                    ok = false;
                }
            } catch (SAXParseException e) {
                log.error(sm.getString("contextConfig.defaultParse"), e);
                log.error(sm.getString("contextConfig.defaultPosition",
                                 "" + e.getLineNumber(),
                                 "" + e.getColumnNumber()));
                ok = false;
            } catch (Exception e) {
                log.error(sm.getString("contextConfig.defaultParse"), e);
                ok = false;
            } finally {
                parseException = null;
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (IOException e) {
                    log.error(sm.getString("contextConfig.defaultClose"), e);
                }
            }
        }
    }

    
    /**
     * Adjust docBase.
     */
    protected void fixDocBase()
        throws IOException {
        
        Host host = (Host) context.getParent();
        String appBase = host.getAppBase();

        boolean unpackWARs = true;
        if (host instanceof StandardHost) {
            unpackWARs = ((StandardHost) host).isUnpackWARs() 
                && ((StandardContext) context).getUnpackWAR();
        }

        File canonicalAppBase = new File(appBase);
        if (canonicalAppBase.isAbsolute()) {
            canonicalAppBase = canonicalAppBase.getCanonicalFile();
        } else {
            canonicalAppBase = 
                new File(System.getProperty("catalina.base"), appBase)
                .getCanonicalFile();
        }

        String docBase = context.getDocBase();
        if (docBase == null) {
            // Trying to guess the docBase according to the path
            String path = context.getPath();
            if (path == null) {
                return;
            }
            if (path.equals("")) {
                docBase = "ROOT";
            } else {
                if (path.startsWith("/")) {
                    docBase = path.substring(1);
                } else {
                    docBase = path;
                }
            }
        }

        File file = new File(docBase);
        if (!file.isAbsolute()) {
            docBase = (new File(canonicalAppBase, docBase)).getPath();
        } else {
            docBase = file.getCanonicalPath();
        }

        if (docBase.toLowerCase().endsWith(".war") && unpackWARs) {
            URL war = new URL("jar:" + (new File(docBase)).toURL() + "!/");
            String contextPath = context.getPath();
            if (contextPath.equals("")) {
                contextPath = "ROOT";
            }
            docBase = ExpandWar.expand(host, war, contextPath);
            file = new File(docBase);
            docBase = file.getCanonicalPath();
        } else {
            File docDir = new File(docBase);
            if (!docDir.exists()) {
                File warFile = new File(docBase + ".war");
                if (warFile.exists()) {
                    if (unpackWARs) {
                        URL war = new URL("jar:" + warFile.toURL() + "!/");
                        docBase = ExpandWar.expand(host, war, context.getPath());
                        file = new File(docBase);
                        docBase = file.getCanonicalPath();
                    } else {
                        docBase = warFile.getCanonicalPath();
                    }
                }
            }
        }

        if (docBase.startsWith(canonicalAppBase.getPath())) {
            docBase = docBase.substring(canonicalAppBase.getPath().length());
            docBase = docBase.replace(File.separatorChar, '/');
            if (docBase.startsWith("/")) {
                docBase = docBase.substring(1);
            }
        } else {
            docBase = docBase.replace(File.separatorChar, '/');
        }

        context.setDocBase(docBase);

    }
    
    
    protected void antiLocking()
        throws IOException {

        if ((context instanceof StandardContext) 
            && ((StandardContext) context).getAntiResourceLocking()) {
            
            Host host = (Host) context.getParent();
            String appBase = host.getAppBase();
            String docBase = context.getDocBase();
            if (docBase == null)
                return;
            if (originalDocBase == null) {
                originalDocBase = docBase;
            } else {
                docBase = originalDocBase;
            }
            File docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(appBase, docBase);
            }
            
            String path = context.getPath();
            if (path == null) {
                return;
            }
            if (path.equals("")) {
                docBase = "ROOT";
            } else {
                if (path.startsWith("/")) {
                    docBase = path.substring(1);
                } else {
                    docBase = path;
                }
            }

            File file = null;
            if (docBase.toLowerCase().endsWith(".war")) {
                file = new File(System.getProperty("java.io.tmpdir"),
                        deploymentCount++ + "-" + docBase + ".war");
            } else {
                file = new File(System.getProperty("java.io.tmpdir"), 
                        deploymentCount++ + "-" + docBase);
            }
            
            if (log.isDebugEnabled())
                log.debug("Anti locking context[" + context.getPath() 
                        + "] setting docBase to " + file);
            
            // Cleanup just in case an old deployment is lying around
            ExpandWar.delete(file);
            if (ExpandWar.copy(docBaseFile, file)) {
                context.setDocBase(file.getAbsolutePath());
            }
            
        }
        
    }
    

    /**
     * Process a "init" event for this Context.
     */
    protected void init() {
        // Called from StandardContext.init()

        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.init"));
        context.setConfigured(false);
        ok = true;
        
        contextConfig();
        
        try {
            fixDocBase();
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.fixDocBase"), e);
        }
        
    }
    
    
    /**
     * Process a "before start" event for this Context.
     */
    protected synchronized void beforeStart() {
        
        try {
            antiLocking();
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.antiLocking"), e);
        }
        
    }
    
    
    /**
     * Process a "start" event for this Context.
     */
    protected synchronized void start() {
        // Called from StandardContext.start()

        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.start"));

        // Set properties based on DefaultContext
        Container container = context.getParent();
        if( !context.getOverride() ) {
            if( container instanceof Host ) {
                // Reset the value only if the attribute wasn't
                // set on the context.
                xmlValidation = context.getXmlValidation();
                if (!xmlValidation) {
                    xmlValidation = ((Host)container).getXmlValidation();
                }
                
                xmlNamespaceAware = context.getXmlNamespaceAware();
                if (!xmlNamespaceAware){
                    xmlNamespaceAware 
                                = ((Host)container).getXmlNamespaceAware();
                }

                container = container.getParent();
            }
        }

        // Process the default and application web.xml files
        defaultWebConfig();
        applicationWebConfig();
        if (ok) {
            validateSecurityRoles();
        }

        // Configure an authenticator if we need one
        if (ok)
            authenticatorConfig();

        // Configure a manager
        if (ok)
            managerConfig();

        // Dump the contents of this pipeline if requested
        if ((log.isDebugEnabled()) && (context instanceof ContainerBase)) {
            log.debug("Pipline Configuration:");
            Pipeline pipeline = ((ContainerBase) context).getPipeline();
            Valve valves[] = null;
            if (pipeline != null)
                valves = pipeline.getValves();
            if (valves != null) {
                for (int i = 0; i < valves.length; i++) {
                    log.debug("  " + valves[i].getInfo());
                }
            }
            log.debug("======================");
        }

        // Make our application available if no problems were encountered
        if (ok)
            context.setConfigured(true);
        else {
            log.error(sm.getString("contextConfig.unavailable"));
            context.setConfigured(false);
        }

    }


    /**
     * Process a "stop" event for this Context.
     */
    protected synchronized void stop() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.stop"));

        int i;

        // Removing children
        Container[] children = context.findChildren();
        for (i = 0; i < children.length; i++) {
            context.removeChild(children[i]);
        }

        // Removing application parameters
        /*
        ApplicationParameter[] applicationParameters =
            context.findApplicationParameters();
        for (i = 0; i < applicationParameters.length; i++) {
            context.removeApplicationParameter
                (applicationParameters[i].getName());
        }
        */

        // Removing security constraints
        SecurityConstraint[] securityConstraints = context.findConstraints();
        for (i = 0; i < securityConstraints.length; i++) {
            context.removeConstraint(securityConstraints[i]);
        }

        // Removing Ejbs
        /*
        ContextEjb[] contextEjbs = context.findEjbs();
        for (i = 0; i < contextEjbs.length; i++) {
            context.removeEjb(contextEjbs[i].getName());
        }
        */

        // Removing environments
        /*
        ContextEnvironment[] contextEnvironments = context.findEnvironments();
        for (i = 0; i < contextEnvironments.length; i++) {
            context.removeEnvironment(contextEnvironments[i].getName());
        }
        */

        // Removing errors pages
        ErrorPage[] errorPages = context.findErrorPages();
        for (i = 0; i < errorPages.length; i++) {
            context.removeErrorPage(errorPages[i]);
        }

        // Removing filter defs
        FilterDef[] filterDefs = context.findFilterDefs();
        for (i = 0; i < filterDefs.length; i++) {
            context.removeFilterDef(filterDefs[i]);
        }

        // Removing filter maps
        FilterMap[] filterMaps = context.findFilterMaps();
        for (i = 0; i < filterMaps.length; i++) {
            context.removeFilterMap(filterMaps[i]);
        }

        // Removing local ejbs
        /*
        ContextLocalEjb[] contextLocalEjbs = context.findLocalEjbs();
        for (i = 0; i < contextLocalEjbs.length; i++) {
            context.removeLocalEjb(contextLocalEjbs[i].getName());
        }
        */

        // Removing Mime mappings
        String[] mimeMappings = context.findMimeMappings();
        for (i = 0; i < mimeMappings.length; i++) {
            context.removeMimeMapping(mimeMappings[i]);
        }

        // Removing parameters
        String[] parameters = context.findParameters();
        for (i = 0; i < parameters.length; i++) {
            context.removeParameter(parameters[i]);
        }

        // Removing resource env refs
        /*
        String[] resourceEnvRefs = context.findResourceEnvRefs();
        for (i = 0; i < resourceEnvRefs.length; i++) {
            context.removeResourceEnvRef(resourceEnvRefs[i]);
        }
        */

        // Removing resource links
        /*
        ContextResourceLink[] contextResourceLinks =
            context.findResourceLinks();
        for (i = 0; i < contextResourceLinks.length; i++) {
            context.removeResourceLink(contextResourceLinks[i].getName());
        }
        */

        // Removing resources
        /*
        ContextResource[] contextResources = context.findResources();
        for (i = 0; i < contextResources.length; i++) {
            context.removeResource(contextResources[i].getName());
        }
        */

        // Removing sercurity role
        String[] securityRoles = context.findSecurityRoles();
        for (i = 0; i < securityRoles.length; i++) {
            context.removeSecurityRole(securityRoles[i]);
        }

        // Removing servlet mappings
        String[] servletMappings = context.findServletMappings();
        for (i = 0; i < servletMappings.length; i++) {
            context.removeServletMapping(servletMappings[i]);
        }

        // FIXME : Removing status pages

        // Removing taglibs
        String[] taglibs = context.findTaglibs();
        for (i = 0; i < taglibs.length; i++) {
            context.removeTaglib(taglibs[i]);
        }

        // Removing welcome files
        String[] welcomeFiles = context.findWelcomeFiles();
        for (i = 0; i < welcomeFiles.length; i++) {
            context.removeWelcomeFile(welcomeFiles[i]);
        }

        // Removing wrapper lifecycles
        String[] wrapperLifecycles = context.findWrapperLifecycles();
        for (i = 0; i < wrapperLifecycles.length; i++) {
            context.removeWrapperLifecycle(wrapperLifecycles[i]);
        }

        // Removing wrapper listeners
        String[] wrapperListeners = context.findWrapperListeners();
        for (i = 0; i < wrapperListeners.length; i++) {
            context.removeWrapperListener(wrapperListeners[i]);
        }

        // Remove (partially) folders and files created by antiLocking
        Host host = (Host) context.getParent();
        String appBase = host.getAppBase();
        String docBase = context.getDocBase();
        if ((docBase != null) && (originalDocBase != null)) {
            File docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                docBaseFile = new File(appBase, docBase);
            }
            ExpandWar.delete(docBaseFile);
        }
        
        ok = true;

    }
    
    
    /**
     * Process a "destroy" event for this Context.
     */
    protected synchronized void destroy() {
        // Called from StandardContext.destroy()
        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.destroy"));
        String workDir = ((StandardContext) context).getWorkDir();
        if (workDir != null)
            ExpandWar.delete(new File(workDir));
    }
    
    
    /**
     * Validate the usage of security role names in the web application
     * deployment descriptor.  If any problems are found, issue warning
     * messages (for backwards compatibility) and add the missing roles.
     * (To make these problems fatal instead, simply set the <code>ok</code>
     * instance variable to <code>false</code> as well).
     */
    protected void validateSecurityRoles() {

        // Check role names used in <security-constraint> elements
        SecurityConstraint constraints[] = context.findConstraints();
        for (int i = 0; i < constraints.length; i++) {
            String roles[] = constraints[i].findAuthRoles();
            for (int j = 0; j < roles.length; j++) {
                if (!"*".equals(roles[j]) &&
                    !context.findSecurityRole(roles[j])) {
                    log.info(sm.getString("contextConfig.role.auth", roles[j]));
                    context.addSecurityRole(roles[j]);
                }
            }
        }

        // Check role names used in <servlet> elements
        Container wrappers[] = context.findChildren();
        for (int i = 0; i < wrappers.length; i++) {
            Wrapper wrapper = (Wrapper) wrappers[i];
            String runAs = wrapper.getRunAs();
            if ((runAs != null) && !context.findSecurityRole(runAs)) {
                log.info(sm.getString("contextConfig.role.runas", runAs));
                context.addSecurityRole(runAs);
            }
            String names[] = wrapper.findSecurityReferences();
            for (int j = 0; j < names.length; j++) {
                String link = wrapper.findSecurityReference(names[j]);
                if ((link != null) && !context.findSecurityRole(link)) {
                    log.info(sm.getString("contextConfig.role.link", link));
                    context.addSecurityRole(link);
                }
            }
        }

    }


    /**
     * Get config base.
     */
    protected File getConfigBase() {
        File configBase = 
            new File(System.getProperty("catalina.base"), "conf");
        if (!configBase.exists()) {
            return null;
        }
        Container container = context;
        Container host = null;
        Container engine = null;
        while (container != null) {
            if (container instanceof Host)
                host = container;
            if (container instanceof Engine)
                engine = container;
            container = container.getParent();
        }
        if (engine != null) {
            configBase = new File(configBase, engine.getName());
        }
        if (host != null) {
            configBase = new File(configBase, host.getName());
        }
        configBase.mkdirs();
        return configBase;
    }


    protected class ContextErrorHandler
        implements ErrorHandler {

        public void error(SAXParseException exception) {
            parseException = exception;
        }

        public void fatalError(SAXParseException exception) {
            parseException = exception;
        }

        public void warning(SAXParseException exception) {
            parseException = exception;
        }

    }


}
