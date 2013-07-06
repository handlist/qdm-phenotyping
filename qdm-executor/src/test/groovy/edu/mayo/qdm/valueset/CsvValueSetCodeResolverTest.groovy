package edu.mayo.qdm.valueset

import org.junit.Test

import static org.junit.Assert.*

/**
 */
class CsvValueSetCodeResolverTest {

    @Test
    void TestGetCodes(){
        def resolver = new CsvValueSetCodeResolver()
        resolver.afterPropertiesSet()

        def codes = resolver.resolveConcpets("2.16.840.1.113883.3.117.1.7.1.212")

        assertTrue codes.size() > 0

        print codes
    }
}


