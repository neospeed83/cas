package org.apereo.cas.config;

import org.apereo.cas.authentication.principal.PrincipalFactory;
import org.apereo.cas.authentication.principal.ServiceFactory;
import org.apereo.cas.authentication.principal.ServiceMatchingStrategy;
import org.apereo.cas.authentication.principal.WebApplicationService;
import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.configuration.features.CasFeatureModule;
import org.apereo.cas.logout.LogoutManager;
import org.apereo.cas.services.ServicesManager;
import org.apereo.cas.ticket.Ticket;
import org.apereo.cas.ticket.TicketCatalog;
import org.apereo.cas.ticket.TicketFactory;
import org.apereo.cas.ticket.registry.NoOpTicketRegistryCleaner;
import org.apereo.cas.ticket.registry.ServiceTicketCompactor;
import org.apereo.cas.ticket.registry.ShortenedServiceMatchingStrategy;
import org.apereo.cas.ticket.registry.StatelessTicketRegistry;
import org.apereo.cas.ticket.registry.TicketCompactor;
import org.apereo.cas.ticket.registry.TicketGrantingTicketCompactor;
import org.apereo.cas.ticket.registry.TicketRegistry;
import org.apereo.cas.ticket.registry.TicketRegistryCleaner;
import org.apereo.cas.ticket.registry.TransientSessionTicketCompactor;
import org.apereo.cas.ticket.serialization.TicketSerializationManager;
import org.apereo.cas.util.CoreTicketUtils;
import org.apereo.cas.util.crypto.CipherExecutor;
import org.apereo.cas.util.spring.boot.ConditionalOnFeatureEnabled;
import org.apereo.cas.web.flow.CasWebflowConfigurer;
import org.apereo.cas.web.flow.CasWebflowConstants;
import org.apereo.cas.web.flow.CasWebflowExecutionPlanConfigurer;
import org.apereo.cas.web.flow.StatelessTicketRegistryWebflowConfigurer;
import lombok.val;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.webflow.definition.registry.FlowDefinitionRegistry;
import org.springframework.webflow.engine.builder.support.FlowBuilderServices;
import java.util.List;

/**
 * This is {@link CasStatelessTicketRegistryAutoConfiguration}.
 *
 * @author Misagh Moayyed
 * @since 7.0.0
 */
@ConditionalOnFeatureEnabled(feature = CasFeatureModule.FeatureCatalog.TicketRegistry, module = "stateless")
@AutoConfiguration
public class CasStatelessTicketRegistryAutoConfiguration {

    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @Lazy(false)
    public TicketRegistryCleaner ticketRegistryCleaner() {
        return NoOpTicketRegistryCleaner.getInstance();
    }
    
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    @ConditionalOnMissingBean(name = "statelessTicketRegistryCipherExecutor")
    public CipherExecutor statelessTicketRegistryCipherExecutor(final CasConfigurationProperties casProperties) {
        val stateless = casProperties.getTicket().getRegistry().getStateless();
        return CoreTicketUtils.newTicketRegistryCipherExecutor(stateless.getCrypto(), "stateless");
    }

    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public TicketRegistry ticketRegistry(
        final List<TicketCompactor<Ticket>> ticketCompactors,
        @Qualifier(TicketCatalog.BEAN_NAME)
        final TicketCatalog ticketCatalog,
        @Qualifier(TicketSerializationManager.BEAN_NAME)
        final TicketSerializationManager ticketSerializationManager,
        @Qualifier(LogoutManager.DEFAULT_BEAN_NAME)
        final ObjectProvider<LogoutManager> logoutManager,
        final CasConfigurationProperties casProperties) {
        val cipher = CoreTicketUtils.newTicketRegistryCipherExecutor(
            casProperties.getTicket().getRegistry().getStateless().getCrypto(), "stateless");
        return new StatelessTicketRegistry(cipher, ticketSerializationManager, ticketCatalog, ticketCompactors);
    }

    @Bean
    @ConditionalOnMissingBean(name = "statelessTicketRegistryWebflowExecutionPlanConfigurer")
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public CasWebflowExecutionPlanConfigurer statelessTicketRegistryWebflowExecutionPlanConfigurer(
        @Qualifier("statelessTicketRegistryWebflowConfigurer")
        final CasWebflowConfigurer surrogateWebflowConfigurer) {
        return plan -> plan.registerWebflowConfigurer(surrogateWebflowConfigurer);
    }

    @ConditionalOnMissingBean(name = "statelessTicketRegistryWebflowConfigurer")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public CasWebflowConfigurer statelessTicketRegistryWebflowConfigurer(
        @Qualifier(CasWebflowConstants.BEAN_NAME_ACCOUNT_PROFILE_FLOW_DEFINITION_REGISTRY)
        final ObjectProvider<FlowDefinitionRegistry> accountProfileFlowRegistry,
        @Qualifier(CasWebflowConstants.BEAN_NAME_FLOW_BUILDER_SERVICES)
        final FlowBuilderServices flowBuilderServices,
        @Qualifier(CasWebflowConstants.BEAN_NAME_LOGIN_FLOW_DEFINITION_REGISTRY)
        final FlowDefinitionRegistry loginFlowDefinitionRegistry,
        final CasConfigurationProperties casProperties,
        final ConfigurableApplicationContext applicationContext) {
        return new StatelessTicketRegistryWebflowConfigurer(flowBuilderServices,
            loginFlowDefinitionRegistry, accountProfileFlowRegistry, applicationContext, casProperties);
    }

    @ConditionalOnMissingBean(name = "ticketGrantingTicketCompactor")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public TicketCompactor ticketGrantingTicketCompactor(
        @Qualifier(TicketSerializationManager.BEAN_NAME)
        final TicketSerializationManager ticketSerializationManager) {
        return new TicketGrantingTicketCompactor(ticketSerializationManager);
    }

    @ConditionalOnMissingBean(name = "serviceTicketCompactor")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public TicketCompactor serviceTicketCompactor(
        @Qualifier(PrincipalFactory.BEAN_NAME)
        final PrincipalFactory principalFactory,
        @Qualifier(WebApplicationService.BEAN_NAME_FACTORY)
        final ServiceFactory serviceFactory,
        @Qualifier(TicketFactory.BEAN_NAME)
        final ObjectProvider<TicketFactory> ticketFactory) {
        return new ServiceTicketCompactor(ticketFactory, serviceFactory, principalFactory);
    }

    @ConditionalOnMissingBean(name = "transientTicketCompactor")
    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public TicketCompactor transientTicketCompactor(
        @Qualifier(WebApplicationService.BEAN_NAME_FACTORY)
        final ServiceFactory serviceFactory,
        @Qualifier(TicketFactory.BEAN_NAME)
        final ObjectProvider<TicketFactory> ticketFactory) {
        return new TransientSessionTicketCompactor(ticketFactory, serviceFactory);
    }

    @Bean
    @RefreshScope(proxyMode = ScopedProxyMode.DEFAULT)
    public ServiceMatchingStrategy serviceMatchingStrategy(
        @Qualifier(ServicesManager.BEAN_NAME) final ServicesManager servicesManager) {
        return new ShortenedServiceMatchingStrategy(servicesManager);
    }
}
