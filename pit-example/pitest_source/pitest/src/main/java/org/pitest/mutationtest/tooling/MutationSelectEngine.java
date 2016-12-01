package org.pitest.mutationtest.tooling;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.pitest.mutationtest.DetectionStatus;
import org.pitest.mutationtest.MutationMetaData;
import org.pitest.mutationtest.MutationResult;
import org.pitest.mutationtest.build.MutationAnalysisUnit;
import org.pitest.mutationtest.build.MutationTestUnit;
import org.pitest.mutationtest.engine.MutationDetails;

public class MutationSelectEngine {
	
	private List<MutationAnalysisUnit> allMAU; //obtained from first iteration outside the loop TUS
	private List<Map<String,Integer>> categPriorityPerMAU;
	private final int nb_mutations = 6;
	public Set<String> mutatorNames = new HashSet<String>();
	
	//Zak{
	public int totalMutations = 0;
	public double perRun = 0;
	public double MSC = 0;
	//}

	public MutationSelectEngine(List<MutationAnalysisUnit> tus){
		allMAU = tus;
		categPriorityPerMAU = new ArrayList<Map<String,Integer>>();
		
		for(int i = 0; i < tus.size(); ++i) { 
			categPriorityPerMAU.add(new HashMap<String, Integer>()); 
			for (MutationDetails md: ((MutationTestUnit)allMAU.get(i)).AllMutationState.allMutations()) {
				mutatorNames.add(md.getMutator());
				
				//Zak{
				totalMutations++;
				//}
				
				if(categPriorityPerMAU.get(i).get(md.getMutator()) == null)
					categPriorityPerMAU.get(i).put(md.getMutator(), 1);		
			}
		}
	}
	
	//one mutation per category (mutator)
	public void initialize() {
        for( MutationAnalysisUnit mau : allMAU ) {
        	Collection<MutationDetails> MD = ((MutationTestUnit) mau).getMutations();

        	//Find all the mutator types.
        	Set <String> categ_mut = new HashSet<String>();
        	for (MutationDetails md: MD){
        		categ_mut.add(md.getMutator());
        	}
		
        	//Schedule one type from each mutator type at the beginning.
        	for( String mutator_type : categ_mut ) {
        		for (MutationDetails md : ((MutationTestUnit) mau).AllMutationState.mutationMap.keySet()) {
        			if(md.getMutator().equals(mutator_type)) {
        				((MutationTestUnit) mau).AllMutationState.setStatusForMutation( md, DetectionStatus.NOT_STARTED);
        				break;
        			}
        		}
        	}
        }
	}

	//TODO: make sure about the KILLED status.
	public List<Map<String, Integer>> constructAlive() {
		List<Map<String, Integer>> mauAliveSet = new ArrayList<Map<String, Integer>>();
		for(MutationAnalysisUnit mau : allMAU ) {
			Map<String, Integer> categ = new HashMap<String, Integer>();
			MutationMetaData mau_mmd = MutationTestUnit.reportResults(((MutationTestUnit) mau).AllMutationState);
			for(MutationResult mr : mau_mmd.getMutations()) {
				//FIXME: need to check how this state works
				if(mr.getStatus() == DetectionStatus.SURVIVED) {
					if(categ.get(mr.getDetails().getMutator()) == null )
						categ.put(mr.getDetails().getMutator(), 0);
					categ.put(mr.getDetails().getMutator(), categ.get(mr.getDetails().getMutator()) + 1);
				}
			}
			mauAliveSet.add(categ);
		}
		return mauAliveSet;
	}
	
	//Update priority
	//TODO: anyway to have path information included in the results?
	public void update() {
		List<Map<String, Integer>> categoriesPerMAU = new ArrayList<Map<String, Integer>>(constructAlive());
		for(int i = 0; i < categoriesPerMAU.size(); ++i) {
			for (String categ: categoriesPerMAU.get(i).keySet()) {
				categPriorityPerMAU.get(i).put(categ, categoriesPerMAU.get(i).get(categ));
			}
		}
	}
	
	
	// choose categories: certain percentage
	// each category: choose an alive one
	// update mau_per_categ.setmutations	
	
	// use: update prior_categ to read priority and choose randomly
	// for example: 70% from highest prioriy and 30% for lowest
	// return tus
	// mutants_alive is argument for run in mutation
	public void selectMutants() {
		//Update the list of priorities first.
		update();
		
		//Zak{
		int totalRan = 0;
		int totalKilled = 0;
		//} 
		
		for(int i = 0; i < allMAU.size(); ++i)
		{
			//Zak{
			Map<String, List<Integer>> PercentageRan = new HashMap<String, List<Integer>>();
			for (MutationResult mr : MutationTestUnit.reportResults(((MutationTestUnit) allMAU.get(i)).AllMutationState).getMutations()) {
				List<Integer> RanMutants = new ArrayList<Integer>();
				if(PercentageRan.get(mr.getDetails().getMutator()) == null) {
					RanMutants = Arrays.asList(0,0);
				} else {
					RanMutants = PercentageRan.get(mr.getDetails().getMutator());
				}
				if(mr.getStatus() == DetectionStatus.NOT_SCHEDULED) {
					RanMutants.set(0, RanMutants.get(0)+1);
				} else {
					totalRan++;
					RanMutants.set(0, RanMutants.get(0)+1);
					RanMutants.set(1, RanMutants.get(1)+1);
				}
				System.out.println(mr.getDetails().getMutator() + ": " + mr.getStatusDescription());

				PercentageRan.put(mr.getDetails().getMutator(), RanMutants);
				if(mr.getStatus() == DetectionStatus.KILLED || mr.getStatus() == DetectionStatus.TIMED_OUT) 
					totalKilled++;
			}
			if(totalRan == totalMutations) break;
			
			for(String mutator: PercentageRan.keySet()){
				int ran = PercentageRan.get(mutator).get(1);
				int total = PercentageRan.get(mutator).get(0);
				if ((ran/total) >= 1) { categPriorityPerMAU.get(i).put(mutator, 0); } //If all ran, make the priority zero. 
			}
			//}
			
			
			//then pick the correct type.
			Map<String, Integer> nextBudget = new HashMap<String, Integer>();
			int sum = 0;
			for(String categ : categPriorityPerMAU.get(i).keySet()) {
				nextBudget.put(categ, categPriorityPerMAU.get(i).get(categ));
				sum += categPriorityPerMAU.get(i).get(categ); 
			}
			
			if(sum != 0)
			{
				// got number of mutations per category 
				for(String categ : nextBudget.keySet())
					nextBudget.put(categ, (nextBudget.get(categ) * nb_mutations)/sum);

				for( String mutator_type : nextBudget.keySet() ) {
					if(nextBudget.get(mutator_type).equals( 0 ))
						continue;
					for (MutationResult mr : MutationTestUnit.reportResults(((MutationTestUnit) allMAU.get(i)).AllMutationState).getMutations()) {
						if(mr.getDetails().getMutator().equals(mutator_type) && (mr.getStatus() == DetectionStatus.NOT_SCHEDULED)) {
							((MutationTestUnit) allMAU.get(i)).AllMutationState.setStatusForMutation(mr.getDetails(), DetectionStatus.NOT_STARTED);
							if( nextBudget.get(mutator_type).equals( 1 ))
								break;
							else
								nextBudget.put(mutator_type, nextBudget.get(mutator_type) - 1);
						}
					}
				}
			}
		}
		
		//Zak{
		perRun = (double) totalRan / totalMutations;
		MSC = (double) totalKilled / totalRan;
		//}
	}
}
