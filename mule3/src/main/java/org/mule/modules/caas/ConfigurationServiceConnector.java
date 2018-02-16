package org.mule.modules.caas;

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.apache.commons.lang3.StringUtils;
import org.mule.api.MuleContext;
import org.mule.api.annotations.Config;
import org.mule.api.annotations.Connector;
import org.mule.api.annotations.Processor;
import org.mule.api.transformer.DataType;
import org.mule.devkit.api.transformer.DefaultTranformingValue;
import org.mule.devkit.api.transformer.TransformingValue;
import org.mule.modules.caas.client.DefaultApplicationDataProvider;
import org.mule.modules.caas.config.ConnectorConfig;
import org.mule.modules.caas.model.ApplicationConfiguration;
import org.mule.modules.caas.model.ApplicationConfigurationBuilder;
import org.mule.modules.caas.model.ApplicationDocument;
import org.mule.transformer.types.SimpleDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.PreferencesPlaceholderConfigurer;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Connector(name="configuration-service", friendlyName="Configuration Service")
public class ConfigurationServiceConnector extends PreferencesPlaceholderConfigurer {

	private static final Logger logger = LoggerFactory.getLogger(ConfigurationServiceConnector.class);
	
	@Inject
	private MuleContext context;
	
    @Config
    ConnectorConfig config;

    private ApplicationConfiguration appConfig;

    public ConnectorConfig getConfig() {
        return config;
    }

    public void setConfig(ConnectorConfig config) {
        this.config = config;
    }
    
    @PostConstruct
    public void setup() throws Exception {
    	
    	logger.debug("Setting up connector with properties: {}", config);

    	//standard for rest clients.
    	Client client = ClientBuilder.newClient();
    	client.register(JacksonJsonProvider.class);

    	ApplicationDataProvider provider = new DefaultApplicationDataProvider(config.getConfigServerBaseUrl(), client);

    	appConfig = loadApplicationConfiguration(provider, resolveApplicationName(), config.getVersion(), config.getEnvironment());
    }
    
    @Override
    protected String resolvePlaceholder(String placeholder, Properties p) {
    	logger.debug("Call to resolve placeholder: {}", placeholder);
    	
    	String value = this.appConfig.readProperty(placeholder);

    	if (value != null) {
    		logger.debug("Found key in config server");
    		return value;
    	}
    	
    	logger.debug("Key not found in config server, resolving in the traditional way");
    	return super.resolvePlaceholder(placeholder, p);
    }

    
    private String resolveApplicationName() {
    	
    	String app = config.getApplicationName();
    	
    	if (logger.isDebugEnabled()) logger.debug("Found app name: {}", app);
    	
    	if (StringUtils.isEmpty(app)) {
    		app = context.getConfiguration().getId();
    		
    		if (logger.isDebugEnabled()) logger.debug("Detected app name: {}", app);
    	}
    	
    	if (StringUtils.isEmpty(app)) {
    		logger.error("App name could not be detected");
    		throw new IllegalArgumentException("Could not detect application name from context or configuration.");
    	}
    	
    	
    	if (logger.isDebugEnabled()) logger.debug("Detected app name: {} ", app);
    	
    	return app;
    	
    }

	public void setContext(MuleContext context) {
		this.context = context;
	}


	@Processor
	public TransformingValue<InputStream, DataType<InputStream>> readDocument(String key) throws ConfigurationNotFoundException {

        ApplicationDocument doc = appConfig.findDocument(key);

        if (doc == null) throw new ConfigurationNotFoundException("Could not find document " + key + " in application " + appConfig.getName());

        Client client = ClientBuilder.newClient();
        client.register(JacksonJsonProvider.class);

        ApplicationDataProvider provider = new DefaultApplicationDataProvider(config.getConfigServerBaseUrl(), client);

        return new DefaultTranformingValue<>(provider.loadDocument(doc, appConfig), new SimpleDataType<InputStream>(InputStream.class, doc.getContentType()));
    }

    /**
     * Recursive method to read from the configuration service an app and its parents.
     * @param name the application name to read.
     * @param version the version to read.
     * @param environment the environment.
     * @return an application configuration.
     */
    protected ApplicationConfiguration loadApplicationConfiguration(ApplicationDataProvider provider, String name, String version, String environment) throws ConfigurationServiceException {

        ApplicationConfigurationBuilder retBuilder = ApplicationConfiguration.builder()
                .setName(name)
                .setEnvironment(environment)
                .setVersion(version);

        //load from the API
        Map<String, Object> appData = provider.loadApplication(name, version, environment);

        //get the properties
        Map<String, String> properties = (Map) appData.get("properties");
        if (properties == null) {
            properties = Collections.emptyMap();
        }

        retBuilder.setProperties(properties);

        //get the parent apps
        List<Map<String, String>> parents = (List) appData.get("parents");

        if (parents == null) {
            parents = Collections.emptyList();
        }

        //go recursively through the parents to build the list.
        for (Map<String, String> parent : parents) {

            String parentName = parent.get("application");
            String parentVersion = parent.get("version");
            String parentEnvironment = parent.get("environment");

            ApplicationConfiguration parentConfig = loadApplicationConfiguration(provider, parentName, parentVersion, parentEnvironment);

            retBuilder.parent(parentConfig);
        }

        //get the documents
        List<Map<String, String>> documents = (List) appData.get("documents");

        if (documents == null) {
            documents = Collections.emptyList();
        }

        for (Map<String, String> document : documents) {
            String key = document.get("key");
            String type = document.get("type");

            retBuilder.document(new ApplicationDocument(type, key));
        }

        return retBuilder.build();
     }

}