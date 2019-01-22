package org.reactome.release.orthopairs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class MappingFileGenerator {

	// Creates the protein-gene mapping file for the species that will be the source for Orthoinference. 
	// Typically this is Human for Reactome and O. sativa (rice) for Plant Reactome.
	public static void createSourceProteinGeneMappingFile(List<String> biomartResults, String sourceSpeciesKey, String speciesName, File biomartFile, File hgncFile) throws IOException {
		
		Map<String,HashSet<String>> proteinGeneMap = new HashMap<>();
		Map<String,HashSet<String>> hgncEnsemblMap = new HashMap<>();
		for (String resultLine : biomartResults) {
			String[] tabSplit = resultLine.split("\t");

			// Biomart results should always be 4-column tab-seperated lines
			if (tabSplit.length > 1) {
				String ensemblGene = tabSplit[0];
				String swissprot = tabSplit[1];
				String trembl = tabSplit[2];
				String hgnc = "";
				if (tabSplit.length > 4) {
					hgnc = tabSplit[4].replace("HGNC:", "");
				}
				
				// Filters for empty fields in the swissprot (2nd) column
				// If they are occupied, adds to the HashMap with the swissprot (protein) ID as the key and the gene ID to the array in the value
				if (!swissprot.equals("")) {
					if (proteinGeneMap.get(swissprot) != null) {
						proteinGeneMap.get(swissprot).add(ensemblGene);
					} else {
						HashSet<String> firstEnsemblAdded = new HashSet<String>(Arrays.asList(ensemblGene));
						proteinGeneMap.put(swissprot, firstEnsemblAdded);
					}
				}
				
				// Filters for empty fields in the trembl (3rd) column
				// If they are occupied, adds to the HashMap with the trembl(protein) ID as the key and the gene ID to the array in the value
				if (!trembl.equals("")) {
					if (proteinGeneMap.get(trembl) != null) {
						proteinGeneMap.get(trembl).add(ensemblGene);
					} else {
						HashSet<String> firstEnsemblAdded = new HashSet<String>(Arrays.asList(ensemblGene));
						proteinGeneMap.put(trembl, firstEnsemblAdded);
					}
				}

				// Panther uses HGNC IDs for Human genes sometimes. This populates the Mapping between these values. 
				if (!hgnc.equals("")) {
					if (hgncEnsemblMap.get(hgnc) != null) {
						hgncEnsemblMap.get(hgnc).add(ensemblGene);
					} else {
						HashSet<String> firstEnsemblAdded = new HashSet<>(Arrays.asList(ensemblGene));
						hgncEnsemblMap.put(hgnc, firstEnsemblAdded);
					}
				}
			}
		}
		
		// Creates and populates output file for source species
		Map<String,ArrayList<String>> valuesSortedProteinGeneMap = new HashMap<String,ArrayList<String>>();
		if (proteinGeneMap.keySet().size() > 0) {
			for (String gene : proteinGeneMap.keySet()) {
				valuesSortedProteinGeneMap.put(gene, sortSet(proteinGeneMap.get(gene)));
			}
			outputMappingFile(biomartFile, valuesSortedProteinGeneMap);
		} else {
			System.out.println("Problem generating Biomart file for " + speciesName);
		}
		// Creates and populates output HGNC-Ensembl Gene mapping file
		Map<String,ArrayList<String>> valuesSortedHGNCEnsemblMap = new HashMap<String,ArrayList<String>>();
		if (hgncEnsemblMap.keySet().size() > 0) {
			for (String hgnc : hgncEnsemblMap.keySet()) {
				valuesSortedHGNCEnsemblMap.put(hgnc, sortSet(hgncEnsemblMap.get(hgnc)));
			}
			outputMappingFile(hgncFile, valuesSortedHGNCEnsemblMap);
		} else {
			System.out.println("Problem generating HGNC file for " + speciesName);
		}
	}

	// Creates the gene-protein mapping file for species that will be inferred to during orthoinference. While this may look
	// similar to the 'createSourceProteinGeneMappingFile' function in this class, the values are actually inverted and the
	// handling of them differs slightly. It felt appropriate to divide these into 2 methods so that it's easier to deconstruct.
	public static void createTargetGeneProteinMappingFile(List<String> biomartResults, String targetSpeciesKey, String speciesName, File biomartFile, File altIdFile) throws IOException {
		
		Set<String> genes = new HashSet<String>();
		Map<String,HashSet<String>> geneSwissprotMap = new HashMap<String,HashSet<String>>();
		Map<String,HashSet<String>> geneTremblMap = new HashMap<String,HashSet<String>>();
		Map<String,HashSet<String>> geneEnsemblMap = new HashMap<String,HashSet<String>>();
		Map<String,HashSet<String>> altIdEnsemblMap = new HashMap<>();
		for (String resultLine : biomartResults) {
			String[] tabSplit = resultLine.split("\t");

			String ensemblGene = "";
			String swissprot = "";
			String trembl = "";
			String ensemblProtein = "";


			// Biomart results
			// S. cerevisiae doesn't contain Trembl IDs, so it only contains 3 columns
			if (tabSplit.length > 1) {
				// lineLength is used to determine which columns contain alternative IDs
				int lineLength = 0;
				if (!targetSpeciesKey.equals("scer")) {
					ensemblGene = tabSplit[0];
					swissprot = tabSplit[1];
					trembl = tabSplit[2];
					ensemblProtein = tabSplit[3];
					lineLength = 4;

				} else {
					ensemblGene = tabSplit[0];
					swissprot = tabSplit[1];
					ensemblProtein = tabSplit[2];
					lineLength = 3;
				}

				// Filters for empty fields in the swissprot column
				// Prepends 'SWISS' to the Swissprot identifier to distinguish it during Orthoinference
				if (!swissprot.equals("")) {
					genes.add(ensemblGene);
					if (geneSwissprotMap.get(ensemblGene) != null) {
						geneSwissprotMap.get(ensemblGene).add("SWISS:" + swissprot);
					} else {
						HashSet<String> firstSwissprotAdded = new HashSet<String>(Arrays.asList("SWISS:" + swissprot));
						geneSwissprotMap.put(ensemblGene, firstSwissprotAdded);
					}
				}

				// Filters for empty fields in the trembl column
				// Prepends 'TREMBL' to the Trembl identifier to distinguish it during Orthoinference
				if (!trembl.equals("")) {
					genes.add(ensemblGene);
					if (geneTremblMap.get(ensemblGene) != null) {
						geneTremblMap.get(ensemblGene).add("TREMBL:" + trembl);
					} else {
						HashSet<String> firstTremblAdded = new HashSet<String>(Arrays.asList("TREMBL:" + trembl));
						geneTremblMap.put(ensemblGene, firstTremblAdded);
					}
				}

				// Filters for empty fields in the Ensembl Protein column
				// Prepends 'ENSP' to the Ensembl Protein identifier to distinguish it during Orthoinference
				if (!ensemblProtein.equals("")) {
					genes.add(ensemblGene);
					if (geneEnsemblMap.get(ensemblGene) != null) {
						geneEnsemblMap.get(ensemblGene).add("ENSP:" + ensemblProtein);
					} else {
						HashSet<String> firstEnsemblAdded = new HashSet<String>(Arrays.asList("ENSP:" + ensemblProtein));
						geneEnsemblMap.put(ensemblGene, firstEnsemblAdded);
					}
				}

				// This populates the Map of alternative gene IDs and ensembl gene IDs
				for (int i = lineLength; i < tabSplit.length; i++) {
					String altId = tabSplit[i];
					if (altIdEnsemblMap.get(altId) != null) {
						altIdEnsemblMap.get(altId).add(ensemblGene);
					} else {
						HashSet<String> firstEnsemblAdded = new HashSet<>(Arrays.asList(ensemblGene));
						altIdEnsemblMap.put(altId, firstEnsemblAdded);
					}
				}
			}
		}
		
		// Only one set of identifiers will be kept, and therefore associated with the gene.
		// Priority is SwissProt > Trembl > Ensembl Protein
		// So if any SwissProt identifiers exist the Trembl and Ensembl Protein identifiers are dropped. 
		// If SwissProt identifiers don't exist, then Trembl is used and Ensembl Protein identifiers are dropped. 
		Map<String,ArrayList<String>> geneProteinMap = new HashMap<String,ArrayList<String>>();
		for (String gene : genes) {
			if (geneSwissprotMap.get(gene) != null) {
				geneProteinMap.put(gene, sortSet(geneSwissprotMap.get(gene)));
			} else if (geneTremblMap.get(gene) != null) {
				geneProteinMap.put(gene, sortSet(geneTremblMap.get(gene)));
			} else {
				geneProteinMap.put(gene, sortSet(geneEnsemblMap.get(gene)));
			}
		}

		String filename = targetSpeciesKey + "_gene_protein_mapping.txt";
		if (geneProteinMap.keySet().size() > 0) {
			outputMappingFile(biomartFile, geneProteinMap);
		} else {
			System.out.println("Problem generating Biomart file for " + speciesName);
		}

		// Creates and populates output alternative Id-Ensembl Gene Id mapping file
		Map<String,ArrayList<String>> valuesSortedaltIdEnsemblMap = new HashMap<String,ArrayList<String>>();
		if (altIdEnsemblMap.keySet().size() > 0) {
			for (String hgnc : altIdEnsemblMap.keySet()) {
				valuesSortedaltIdEnsemblMap.put(hgnc, sortSet(altIdEnsemblMap.get(hgnc)));
			}
			outputMappingFile(altIdFile, valuesSortedaltIdEnsemblMap);
		}
	}
	
	// Values in HashSet are inherently random, so this function converts them to an ArrayList and sorts them
	private static ArrayList<String> sortSet(HashSet<String> valuesSet) {
		
		ArrayList<String> valuesList = new ArrayList<String>(valuesSet);
		Collections.sort(valuesList);
		return valuesList;
	}

	// Outputs the mapping file for both source and target species. Sorts the keys of the incoming map before writing to file. 
	private static void outputMappingFile(File biomartFile, Map<String, ArrayList<String>> biomartMapping) throws IOException {
		
		SortedSet<String> sortedBiomartMapKeys = new TreeSet<String>(biomartMapping.keySet());
		biomartFile.createNewFile();
		for (String key : sortedBiomartMapKeys) {
			Collections.sort(biomartMapping.get(key));
			String outputLine = key + "\t" + String.join(" ", biomartMapping.get(key)) + "\n";
			Files.write(Paths.get(biomartFile.getPath()), outputLine.getBytes(), StandardOpenOption.APPEND);
		}
		
	}

}
