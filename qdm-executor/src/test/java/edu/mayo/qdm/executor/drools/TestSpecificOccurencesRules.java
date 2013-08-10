package edu.mayo.qdm.executor.drools;

import edu.mayo.qdm.patient.Lab;
import edu.mayo.qdm.patient.Patient;
import org.joda.time.DateTime;

import java.util.Arrays;

public class TestSpecificOccurencesRules extends AbstractDroolsTestBase {

    @Override
    protected Iterable<Patient> getPatients() {
        Patient p1 = new Patient("1");
        p1.addLab(new Lab(null,null,new DateTime(1980,1,1,0,0).toDate(),new DateTime(4000,1,1,0,0).toDate()));
        p1.addLab(new Lab(null,null,new DateTime(3000,1,1,0,0).toDate(),new DateTime(4000,1,1,0,0).toDate()));
        p1.addLab(new Lab(null,null,new DateTime(1980,1,1,0,0).toDate(),new DateTime(1980,1,1,0,0).toDate()));

        p1.setBirthdate(new DateTime(1980,1,1,0,0).toDate());
        Patient p2 = new Patient("2");
        p2.setBirthdate(new DateTime(2000, 10, 10, 10, 10).toDate());

        return Arrays.asList(p1,p2);
    }


    @Override
    protected String getDroolsFile() {
        return "specific_occurrences.drl";
    }
}
