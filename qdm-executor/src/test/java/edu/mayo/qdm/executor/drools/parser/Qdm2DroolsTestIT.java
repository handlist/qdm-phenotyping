package edu.mayo.qdm.executor.drools.parser;

import edu.mayo.qdm.executor.MeasurementPeriod;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Date;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:/qdm-executor-context.xml")
public class Qdm2DroolsTestIT {

    @Resource
    private Qdm2Drools qdm2Drools;

    @Test
    public void testCreateRules127() throws IOException {
        this.doTestCreateRules("CMS127v1");
    }

    @Test
    public void testCreateRules124() throws IOException {
        this.doTestCreateRules("CMS124v1");
    }

    @Test
    public void testCreateRules117() throws IOException {
        this.doTestCreateRules("CMS117v1");
    }

    private void doTestCreateRules(String rule) throws IOException {
        String drools = qdm2Drools.qdm2drools(
                IOUtils.toString(
                        new ClassPathResource("qdmxml/" + rule + ".xml").getInputStream()),
                MeasurementPeriod.getCalendarYear(new Date())
        );

        Assert.assertNotNull(drools);
    }
}
