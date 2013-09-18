package edu.mayo.qdm.executor.valueset

import edu.mayo.qdm.patient.Concept
import groovy.json.JsonSlurper
import groovy.util.logging.Log4j
import groovy.xml.Namespace
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import sun.misc.BASE64Encoder

import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

@Log4j
class Cts2ValueSetCodeResolver implements ValueSetCodeResolver, InitializingBean {

    def slurper = new JsonSlurper()
    def parser = new XmlParser()
    def valueSetMap = [] as HashMap
    def codeSystemMap = [] as HashMap<String, String>

    @Value('${cts2.valueSetUrl}')
    private final String cts2Url

    @Value('${cts2.valueSetUser}')
    private final String cts2User

    @Value('${cts2.valueSetPass}')
    private final String cts2Pass

    @Value('${cts2.namespaceResolverUrl}')
    private final String cts2Resolver

    def core = new Namespace("http://schema.omg.org/spec/CTS2/1.0/Core")

    @Override
    void afterPropertiesSet() throws Exception {
        trustSelfSignedSSL()
    }

    @Override
    Set<Concept> resolveConcpets(String valueSetOid) {
        if (valueSetMap.containsKey(valueSetOid)) {
            return valueSetMap.get(valueSetOid)
        } else {
            def url = new URL(cts2Url + "/" + valueSetOid)
            def connection = url.openConnection()
            connection.setRequestProperty("Accept", "application/xml")
            connection.setRequestProperty("Authorization", getAuthorizationHeader())
            connection.connect()
            def inStream = connection.getInputStream()
            def valueSet = parser.parse(inStream)

            def href = valueSet.valueSetCatalogEntry.currentDefinition[core.valueSetDefinition].@href[0]
            def currentDefUrl = new URL(href + "/resolution?maxtoreturn=50000")
            connection = currentDefUrl.openConnection()
            connection.setRequestProperty("Accept", "application/xml")
            connection.setRequestProperty("Authorization", getAuthorizationHeader())
            connection.connect()
            inStream = connection.getInputStream()
            def definition = parser.parse(inStream)
            def codeSystemVersions = [] as HashMap
            definition.resolutionInfo.resolvedUsingCodeSystem.each {
                codeSystemVersions.put(it[core.codeSystem].text(), it[core.version].text())
            }

            // TODO: add support for pagination
            def concepts = [] as Set<Concept>
            //        do {
            concepts.addAll(definition.entry.collect {
                new Concept(it[core.name].text(), it[core.namespace].text(), codeSystemVersions.get(it[core.namespace].text()))
            })
            //        while (definition.iteratableResolvedValueSet.next != null)
            valueSetMap.put(valueSetOid, concepts)
            return concepts
        }
    }

    @Override
    boolean isCodeInSet(String valueSetOid, Concept concept) {
        return this.resolveConcpets(valueSetOid)?.find { it.matches(normalizedConcept(concept))} != null
    }

    private String getAuthorizationHeader() {
        return "Basic " + new BASE64Encoder().encode(
          (cts2User +
          ":" + cts2Pass).getBytes())
    }

    private Concept normalizedConcept(Concept concept) {
        new Concept(concept.code, getNormalizedCodeSystem(concept.codingScheme), concept.codingSchemeVersion)
    }

    private String getNormalizedCodeSystem(String codeSystem) {
        if (codeSystemMap.get(codeSystem) != null) {
            codeSystemMap.get(codeSystem)
        } else {
            def currentDefUrl = new URL(cts2Resolver + "/id/CODE_SYSTEM?id=" + codeSystem)
            def connection = currentDefUrl.openConnection()
            connection.setRequestProperty("Accept", "application/json")
            connection.connect()
            def inStream = connection.getInputStream()
            def resolved = slurper.parse(new InputStreamReader(inStream))
            if (resolved.resourceName != null) {
                String normCodeSystem = resolved.resourceName
                codeSystemMap.put(codeSystem, normCodeSystem)
                normCodeSystem
            } else {
                log.warn("Unable to resolve normalized name for code system " + codeSystem)
                codeSystemMap.put(codeSystem, codeSystem)
                codeSystem
            }
        }
    }

    private void trustSelfSignedSSL() {
        try {
            SSLContext ctx = SSLContext.getInstance("SSL");
            X509TrustManager tm = new X509TrustManager() {

                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }
            };
            ctx.init(null, [tm] as TrustManager[], new SecureRandom());
            SSLContext.setDefault(ctx);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}