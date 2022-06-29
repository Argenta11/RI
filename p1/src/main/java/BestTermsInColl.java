import org.apache.lucene.index.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class BestTermsInColl {

    public static void main(String[] args) throws IOException {

        String usage = "java org.apache.lucene.BestTermsInColl"
                + " [-index INDEX_PATH] [-field FIELD] [-top TOP] [-rev]\n\n"
                + "This shows the best TOP terms of the INDEX_PATH ordered by idflog10"
                + "IF -rev, then it would be ordered by df\n";

        String indexPath = null;
        String field = null;
        int top = -1;
        boolean rev = false;

        for(int i = 0; i< args.length; i++){
            switch (args[i]){
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
                    break;
                case "-top":
                    top = Integer.parseInt(args[++i]);
                    break;
                case "-rev":
                    rev = true;
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        // Check if the arguments required are not null, and the value of top is positive
        if (indexPath == null || field == null || top<0){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        IndexReader indexReader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        // Obtains all the terms of the index with the field FIELD
        Terms terms = MultiTerms.getTerms(indexReader,field);

        if(terms != null){
            final TermsEnum termsEnum = terms.iterator();
            Map<String,Double> frequencies = new HashMap<>();
            BytesRef text = null;
            int numDocs = indexReader.numDocs();

            while ((text = termsEnum.next()) != null){
                String term = text.utf8ToString();

                // Calculates the number of documents that contain the current term
                int freq = termsEnum.docFreq();
                double values;

                if(!rev){
                    // Calculates the value of idflog10
                    values = 1 + Math.log10((1 + (double) numDocs) / (1 + (double) freq));
                } else {
                    // Calculates the value of df
                    values = indexReader.docFreq(new Term(field, text));
                }

                // Stores the term with its value in a Map
                frequencies.put(term, values);
            }
            // Order values from major to minor
            frequencies = orderValues(frequencies);

            if(!rev){
                System.out.println("Los " + top + " mejores términos ordenados por idflog10:");
            } else {
                System.out.println("Los " + top + " mejores términos ordenados por df:");
            }

            // Show the n top terms
            topTerms(frequencies, top);
        }
    }


    /**
     *
     * Orders the values from major to minor
     *
     * @param frequencies Values of the idflog10 or df formulas
     * @return the frequencies ordered
     */

    private static Map<String, Double> orderValues(Map<String, Double> frequencies) {

        List<Map.Entry<String,Double>> list = new LinkedList<Map.Entry<String,Double>>(frequencies.entrySet());

        // Define the function to order elements
        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        // Turns the list over
        Collections.reverse(list);
        Map<String,Double> sortedFrequencies = new LinkedHashMap<String,Double>();
        for (Map.Entry<String,Double> entry: list){
            sortedFrequencies.put(entry.getKey(), entry.getValue());
        }
        return sortedFrequencies;
    }

    /**
     *
     * It prints the top terms with the term and its value (depending on the flag -rev)
     *
     * @param frequencies Values of the idflog10 or df formulas
     * @param top Value that indicates the number of terms we want to show
     */

    private static void topTerms(Map<String, Double> frequencies, int top) {

        System.out.println("Term\t\t\t\tValue");

        int i = 0;
        for (Map.Entry<String, Double> entry : frequencies.entrySet()) {
            // If i is minor than the number top we continue printing
            if (i<top){
                System.out.println("Key : " + entry.getKey()
                        + "\t\t\tValue : " + entry.getValue());
                i++;
            }
            // If that does not occur we finish the iteration as we do not me more terms to print
            else {
                break;
            }

        }
    }
}