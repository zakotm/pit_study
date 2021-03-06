package org.pitest.mutationtest.build;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import org.pitest.classinfo.ClassName;
import org.pitest.coverage.CoverageDatabase;
import org.pitest.coverage.TestInfo;
import org.pitest.functional.FCollection;
import org.pitest.functional.prelude.Prelude;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.util.Log;

/**
 * Assigns tests based on line coverage and order them by execution speed with a
 * weighting towards tests whose names imply they are intended to test the
 * mutated class
 * 
 * @author henry
 *
 */
public class DefaultTestPrioritiser implements TestPrioritiser {

  private static final Logger    LOG                                  = Log
                                                                          .getLogger();

  private static final int       TIME_WEIGHTING_FOR_DIRECT_UNIT_TESTS = 1000;

  private final CoverageDatabase coverage;

  public DefaultTestPrioritiser(CoverageDatabase coverage) {
    this.coverage = coverage;
  }

  @Override
  public List<TestInfo> assignTests(MutationDetails mutation) {
    return prioritizeTests(mutation.getClassName(), pickTests(mutation));
  }

  //Ali This function looks at the coverage information and finds the
  //reachability and returns the possible list of the tests.
  private Collection<TestInfo> pickTests(MutationDetails mutation) {
    if (!mutation.isInStaticInitializer()) 
    {
    	final Collection<TestInfo> temp = this.coverage.getTestsForClassLine(mutation.getClassLine());
    	//Ali: just a modification, if you wanna test.
    	//for (TestInfo t : temp){
    	//	if(t.getName().equals("pitexample.MyClassTest.testMe2(pitexample.MyClassTest)")) {
    	//		temp.remove( t );
    	//	}
    	//}
      return temp;
    } else {
      LOG.warning("Using untargetted tests");
      return this.coverage.getTestsForClass(mutation.getClassName());
    }
  }

  private List<TestInfo> prioritizeTests(ClassName clazz, Collection<TestInfo> testsForMutant) {
    final List<TestInfo> sortedTis = FCollection.map(testsForMutant, Prelude.id(TestInfo.class));
    Collections.sort(sortedTis, new TestInfoPriorisationComparator(clazz,TIME_WEIGHTING_FOR_DIRECT_UNIT_TESTS));
    return sortedTis;
  }
  
  
}