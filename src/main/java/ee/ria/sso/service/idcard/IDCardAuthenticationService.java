package ee.ria.sso.service.idcard;

import com.google.common.base.Splitter;
import ee.ria.sso.Constants;
import ee.ria.sso.authentication.AuthenticationFailedException;
import ee.ria.sso.authentication.AuthenticationType;
import ee.ria.sso.authentication.TaraAuthenticationException;
import ee.ria.sso.authentication.credential.TaraCredential;
import ee.ria.sso.common.AbstractService;
import ee.ria.sso.config.TaraResourceBundleMessageSource;
import ee.ria.sso.config.idcard.IDCardConfigurationProvider;
import ee.ria.sso.statistics.StatisticsHandler;
import ee.ria.sso.statistics.StatisticsOperation;
import ee.ria.sso.utils.X509Utils;
import ee.ria.sso.validators.OCSPValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.webflow.core.collection.SharedAttributeMap;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Map;

@ConditionalOnProperty("idcard.enabled")
@Service
public class IDCardAuthenticationService extends AbstractService {

    private final StatisticsHandler statistics;
    private final IDCardConfigurationProvider configurationProvider;
    private final OCSPValidator ocspValidator;

    public IDCardAuthenticationService(TaraResourceBundleMessageSource messageSource,
                                       StatisticsHandler statistics,
                                       IDCardConfigurationProvider configurationProvider,
                                       OCSPValidator ocspValidator) {
        super(messageSource);
        this.statistics = statistics;
        this.configurationProvider = configurationProvider;
        this.ocspValidator = ocspValidator;
    }

    public Event loginByIDCard(RequestContext context) {
        SharedAttributeMap<Object> sessionMap = this.getSessionMap(context);
        try {
            this.statistics.collect(LocalDateTime.now(), context, AuthenticationType.IDCard, StatisticsOperation.START_AUTH);

            X509Certificate certificate = sessionMap.get(Constants.CERTIFICATE_SESSION_ATTRIBUTE, X509Certificate.class);
            if (certificate == null)
                throw new AuthenticationFailedException("message.idc.nocertificate", "Unable to find certificate from session");
            if (this.configurationProvider.isOcspEnabled())
                this.checkCert(certificate);

            Map<String, String> params = Splitter.on(", ").withKeyValueSeparator("=").split(certificate.getSubjectDN().getName());
            String principalCode = "EE" + params.get("SERIALNUMBER");
            String firstName = params.get("GIVENNAME");
            String lastName = params.get("SURNAME");

            TaraCredential credential = new TaraCredential(AuthenticationType.IDCard, principalCode, firstName, lastName);
            context.getFlowExecutionContext().getActiveSession().getScope().put("credential", credential);

            this.statistics.collect(LocalDateTime.now(), context, AuthenticationType.IDCard, StatisticsOperation.SUCCESSFUL_AUTH);

            return new Event(this, "success");
        } catch (Exception e) {
            throw this.handleException(context, e);
        } finally {
            sessionMap.remove(Constants.CERTIFICATE_SESSION_ATTRIBUTE);
        }
    }

    private RuntimeException handleException(RequestContext context, Exception exception) {
        clearFlowScope(context);
        this.statistics.collect(LocalDateTime.now(), context, AuthenticationType.IDCard, StatisticsOperation.ERROR, exception.getMessage());

        String errorMessageKey = "message.general.error";
        if (exception instanceof AuthenticationFailedException) {
            AuthenticationFailedException authenticationFailedException = (AuthenticationFailedException) exception;
            errorMessageKey = authenticationFailedException.getErrorMessageKeyOrDefault("message.general.error");
        }

        String localizedErrorMessage = this.getMessage(errorMessageKey);
        return new TaraAuthenticationException(localizedErrorMessage, exception);
    }

    private static void clearFlowScope(RequestContext context) {
        context.getFlowScope().clear(); // TODO: is this necessary?
        context.getFlowExecutionContext().getActiveSession().getScope().clear();
    }

    // TODO:

    private void checkCert(X509Certificate x509Certificate) {
        X509Certificate issuerCert = this.findIssuerCertificate(x509Certificate);
        if (issuerCert != null) {
            this.ocspValidator.validate(x509Certificate, issuerCert, configurationProvider.getOcspUrl());
        } else {
            //this.log.error("Issuer cert not found");
            throw new RuntimeException("Issuer cert not found from setup");
        }
    }

    private X509Certificate findIssuerCertificate(X509Certificate userCertificate) {
        String issuerCN = X509Utils.getSubjectCNFromCertificate(userCertificate);
        //log.debug("IssuerCN extracted: {}", issuerCN);
        return configurationProvider.getIssuerCertificates().get(issuerCN);
    }

}