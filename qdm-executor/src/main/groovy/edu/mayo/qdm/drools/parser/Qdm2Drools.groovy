package edu.mayo.qdm.drools.parser
import edu.mayo.qdm.drools.DroolsResults
import edu.mayo.qdm.drools.DroolsUtil
import edu.mayo.qdm.drools.PreconditionResult
import edu.mayo.qdm.drools.parser.criteria.CriteriaFactory
import edu.mayo.qdm.patient.Concept
import edu.mayo.qdm.patient.Patient
import groovy.util.logging.Log4j
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.commons.io.IOUtils
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.InputStreamBody
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
/*
 * The main JSON -> Drools converter.
 */
@Log4j
@Component
class Qdm2Drools {

    def qdm2jsonServiceUrl = "http://qdm2json.herokuapp.com";

    @Autowired
    CriteriaFactory criteriaFactory;

    Qdm2Drools(){
        super()
    }

    Qdm2Drools(String qdm2jsonServiceUrl){
        super()
        this.qdm2jsonServiceUrl = qdm2jsonServiceUrl
    }

    private def getJsonFromQdmFile(qdmXml) {
        def qdmXmlFile = new InputStreamBody(IOUtils.toInputStream(qdmXml), "qdm.xml")

        def http = new HTTPBuilder(this.qdm2jsonServiceUrl + "/qdm2json")

        def resp = http.request(Method.POST) { req ->
            def mpEntity = new MultipartEntity()
            mpEntity.addPart("file", qdmXmlFile)

            req.entity = mpEntity
        }

        resp
    }

    def String qdm2drools(String qdmXml) {
        def json = getJsonFromQdmFile(qdmXml)

        def sb = new StringBuilder()

        sb.append(printRuleHeader(json))

        def usedDataCriteria = [] as HashSet
        json.population_criteria.each {
            printPopulationCriteria( it, sb, usedDataCriteria)
        }

        log.info "Listed Data Criteria Size: ${json.data_criteria.size()}"
        log.info "Used Data Criteria Size: ${usedDataCriteria.size()}"

        json.data_criteria.each {
            if(usedDataCriteria.contains(it.key)){
                sb.append( printDataCriteria( it ) )
            }
        }

        def rule = sb.toString()

        log.isDebugEnabled() ? log.debug(rule):

        System.out.println(rule)

        rule
    }

    private def printRuleHeader(qdm){
        """
        import ${PreconditionResult.name};
        import ${DroolsResults.name};
        import ${Patient.name};
        import ${Concept.name};
        import ${DroolsUtil.name};
        /*
            Title: ${qdm.title}
            Description: ${qdm.description}
        */

        global DroolsResults results
        global DroolsUtil droolsUtil
        """
    }

    private def printPopulationCriteria(populationCriteria, sb, usedDataCriteria){

        def name = populationCriteria.key
        sb.append("""
        /* Rule */
        rule "$name"
            dialect "mvel"
            no-loop
            salience 1

        when
            \$p : Patient( )
            not ( PreconditionResult(id == "$name", patient == \$p) )
            ${switch(name){
                case "DENOM": return """PreconditionResult(id == "IPP", patient == \$p)"""
                case "NUMER": return """PreconditionResult(id == "DENOM", patient == \$p)"""
                default: ""
            }}
        """)

        def nestedPreconditions = []

        def preconditions = populationCriteria.value.preconditions

        if(preconditions?.size() > 0){

            preconditions.eachWithIndex {
                prcn, idx ->
                    def cnj = conjunctionToBoolean(prcn.conjunction_code)

                    if(prcn.reference){
                        def dataCriteriaRef = prcn.reference
                        usedDataCriteria.add(dataCriteriaRef)

                        sb.append(printPreconditionReference(dataCriteriaRef))
                    } else {
                        nestedPreconditions.add(prcn)

                        sb.append(printPreconditionReference(prcn.id))
                    }
                    if(idx != preconditions.size() -1) {
                        sb.append(" ${cnj} ")
                    }
            }
        }
        sb.append("""
        then
            insert(new PreconditionResult("$name", \$p))
            results.add("$name", \$p);
        end
        """)

        printPreconditions( nestedPreconditions, sb, usedDataCriteria )
    }

    private def printPreconditions(preconditions, sb, usedDataCriteria){
        if (preconditions == null) return

        def nestedPreconditions = []

        if(preconditions.size() > 0){

            preconditions.each {
                prcn ->

                    sb.append(
        """
        /* Rule */
        rule "${prcn.id}"
            dialect "mvel"
            no-loop
            salience 1

        when
            \$p : Patient( )
            not ( PreconditionResult(id == "${prcn.id}", patient == \$p) )
        """
                    )

                    if(prcn.reference){
                        def dataCriteriaRef = prcn.reference
                        usedDataCriteria.add(dataCriteriaRef)

                        sb.append(printPreconditionReference(dataCriteriaRef))
                    } else {
                        nestedPreconditions.add(prcn.preconditions)

                        def cnj = conjunctionToBoolean(prcn.conjunction_code)

                        prcn.preconditions.eachWithIndex {
                            nestedPrc, idx ->
                                sb.append(printPreconditionReference(nestedPrc.id))

                                if(idx != prcn.preconditions.size() -1) {
                                    sb.append(" ${cnj} ")
                                }
                        }
                    }

                    sb.append(
        """
        then
            insert(new PreconditionResult("${prcn.id}", \$p))
        end

        """
                    )

            }
        }

        nestedPreconditions.each { printPreconditions(it, sb, usedDataCriteria)}
    }

    private def printPreconditionReference(preconditionReference){
            """
            PreconditionResult( id == "$preconditionReference", patient == \$p )
            """
    }

    private def printDataCriteria(dataCriteria){
        def name = dataCriteria.key
        """
        /* Rule */
        rule "${name}"
            dialect "mvel"
            no-loop
            salience -1

        when
            \$p : Patient(
            ${criteriaFactory.getCriteria(dataCriteria.value).toDrools()}
            )
        then
            insert(new PreconditionResult("${name}", \$p))
        end
        """
    }

    private def conjunctionToBoolean(conjunction){
        if (conjunction == null) return "and"
        switch (conjunction){
            case "allTrue": return "and"
            case "atLeastOneTrue": return "or"
        }
    }

}
