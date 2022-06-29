import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

public class BestTermsInDoc {

    /**
     * Method that calculates the frequencies of the terms
     * @param reader    IndexReader assigned to the index whose path the user passed
     * @param docId     Number of Lucene's document
     * @param field     Field's name
     * @return          Map with pairs term-frequency
     * @throws IOException
     */
    private static Map<String, Integer> getTermFrequencies(IndexReader reader, int docId, String field) throws IOException {

        // Variable initialization
        Terms vector = reader.getTermVector(docId, field);
        Set<String> terms = new HashSet();
        TermsEnum termsEnum = null;
        termsEnum = vector.iterator();
        Map<String, Integer> frequencies = new HashMap();
        BytesRef text = null;

        // Loop in which we will obtain the parameters given by the user
        while((text = termsEnum.next()) != null) {
            String term = text.utf8ToString();
            int freq = (int)termsEnum.totalTermFreq();
            frequencies.put(term, freq);
            terms.add(term);
        }

        return frequencies;
    }

    /**
     * Orders the values from major to minor
     * @param frequencies Values of the idflog10 or df formulas
     * @return the frequencies ordered
     */
    private static Map<String, Double> orderValues(Map<String, Double> frequencies) {

        List<Map.Entry<String,Double>> list = new LinkedList<Map.Entry<String,Double>>(frequencies.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<String, Double>>() {
            @Override
            public int compare(Map.Entry<String, Double> o1, Map.Entry<String, Double> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        Collections.reverse(list);
        Map<String,Double> sortedFrequencies = new LinkedHashMap<String,Double>();
        for (Map.Entry<String,Double> entry: list){
            sortedFrequencies.put(entry.getKey(), entry.getValue());
        }
        return sortedFrequencies;
    }


    /**
     * Method that sorts the map by tf
     * @param   terminos_mapeados   Map with pairs term-frequency that will be sorted
     * @return                      Map with pairs term-frequency sorted
     */
    private static Map<String, Double> tf(Map<String, Integer> terminos_mapeados){

        // Variable initialization
        List<String> tf_list = new ArrayList<>(terminos_mapeados.keySet());
        Map<String, Double> resultado = new HashMap<>();

        // Loop to re-store the terms in a map with their respective values but ordered
        int x=0;
        while( x<tf_list.size()){
            String nombre = tf_list.get(x);
            int valor =terminos_mapeados.get(tf_list.get(x));
            resultado.put(nombre, (double) valor);
            x++;
        }

        // Sort terms, then reverse to order highest to lowest
        resultado=orderValues(resultado);

        return resultado;
    }


    /**
     * Method that sorts the map by df
     * @param   terminos_mapeados   Map with pairs term-frequency that will be sorted
     * @param   indexPath           Path of index
     * @param   field               Field's name
     * @return                      Map with pairs term-frequency sorted
     * @throws IOException
     */
    private static Map<String, Double> df(Map<String, Integer> terminos_mapeados, String indexPath,String field) throws IOException {

        // Variable initialization
        Directory index = FSDirectory.open(Paths.get(indexPath));
        IndexReader indexReader = DirectoryReader.open(index);
        List<String> claves = new ArrayList<>(terminos_mapeados.keySet());
        Map<String,Double> resultado = new HashMap<>();
        Term termino = null;

        // Mapping each term with the corresponding value after applying the function
        int x=0;
        while( x<claves.size()){
            termino = new Term(field,claves.get(x));
            String nombre = claves.get(x);
            double valor = indexReader.docFreq(termino);
            resultado.put(nombre, valor);
            x++;
        }

        // Sort terms, then reverse to order highest to lowest
        resultado=orderValues(resultado);

        return resultado;
    }

    /**
     * Method that sorts the map by df
     * @param   terminos_mapeados   Map with pairs term-frequency that will be sorted
     * @param   indexPath           Path of index
     * @param   field                 Field's name
     * @return                      Map with pairs term-frequency sorted
     * @throws IOException
     */
    private static Map<String, Double> idflog10(Map<String, Integer> terminos_mapeados, String indexPath, String field) throws IOException {

        // Variable initialization
        List<String> claves = new ArrayList<>(terminos_mapeados.keySet());
        Map<String,Double> resultado = new HashMap<>();
        Term termino = null;

        int x=0;

        // Mapping each term with the corresponding value after applying the function
        while( x<claves.size()){
            termino = new Term(field,claves.get(x));
            String nombre = claves.get(x);
            // Call to the method that calculates the idf value
            double valor = obtener_idf(indexPath, termino, field);
            resultado.put(nombre, valor);
            x++;
        }

        // Sort terms, then reverse to order highest to lowest
        resultado=orderValues(resultado);

        return resultado;
    }

    /**
     * Method that calculates term's idf value
     * @param indexPath   Path of index
     * @param term          Term to calculate the idf's value
     * @param field         Field's name
     * @return              Value in double format
     * @throws IOException
     */
    static double obtener_idf(String indexPath, Term term, String field) throws IOException {

        // Using an indexReader for other documents
        Directory index = FSDirectory.open(Paths.get(indexPath));
        DirectoryReader indexReader = DirectoryReader.open(index);

        // Calculations
        double freq = indexReader.docFreq(term);
        double numdocs = indexReader.getDocCount(field);

        return 1+Math.log10((1+numdocs)/(1+freq));
    }


    /**
     * Method that sorts the map by df
     * @param   terminos_mapeados   Map with pairs term-frequency that will be sorted
     * @param   indexPath           Path of index
     * @param   field               Field's name
     * @return                      Map with pairs term-frequency sorted
     * @throws IOException
     */
    private static Map<String, Double> tfxidflog10(Map<String, Integer> terminos_mapeados, String indexPath,String field) throws IOException {

        // Variable initialization
        Map<String, Double> resultado = new HashMap<>();
        List<String> claves = new ArrayList<>(terminos_mapeados.keySet());
        int a=0;

        // Mapping each term with the corresponding value after applying the function
        while(a<terminos_mapeados.size()){
            double tf = terminos_mapeados.get(claves.get(a));
            Term termino = new Term(field,claves.get(a));
            // Call to the method that calculates the idf value
            double idf = obtener_idf(indexPath, termino,field);
            double res = tf*idf;
            String nombre=claves.get(a);
            resultado.put(nombre,res);
            a++;
        }

        // Sort terms, then reverse to order highest to lowest
        resultado=orderValues(resultado);

       return resultado;
    }

    /**
     * Method that prints the output
     * @param list_final        Map used to sort the terms that we will print
     * @param list_tf           Map with pairs term-value of tf
     * @param list_df           Map with pairs term-value of df
     * @param list_idflog10     Map with pairs term-value of idflog10
     * @param list_tfxidflog10  Mapa with pairs term-value of tfxidflog10
     * @param n                 Maximum number of terms that will appear on screen
     */
    private static void imprimirsalida(Map<String, Double> list_final,Map<String, Double> list_tf,Map<String, Double> list_df,Map<String, Double> list_idflog10,Map<String, Double> list_tfxidflog10, int n){

        // Variable initialization
        int pos = 0;

        // Loop in which we will print on the screen the corresponding values in each map for each one of the ordered terms
        for(String key : list_final.keySet()) {
            if(pos<n) {
                String salida = pos + ") " + key + ": " + "tf= " + list_tf.get(key) + " df= " + list_df.get(key) + " idflog10= " + list_idflog10.get(key) + " tfxidflog10= " + list_tfxidflog10.get(key);
                System.out.println(salida);
            }
            pos++;
        }
    }

    /**
     * Method that writes the output in the file whose path was given by the user
     * @param list_final        Map used to sort the terms that we will print
     * @param list_tf           Map with pairs term-value of tf
     * @param list_df           Map with pairs term-value of df
     * @param list_idflog10     Map with pairs term-value of idflog10
     * @param list_tfxidflog10  Mapa with pairs term-value of tfxidflog10
     * @param outputfile        File's path
     * @param n                 Maximum number of terms that will appear on screen
     */
    private static void escribirsalida(Map<String, Double> list_final,Map<String, Double> list_tf,Map<String, Double> list_df,Map<String, Double> list_idflog10,Map<String, Double> list_tfxidflog10, String outputfile, int n) throws IOException {

        // Variable initialization
        int pos = 0;

        // Loop in which we will write to the file the corresponding values in each map for each of the ordered terms
        FileWriter file = new FileWriter(outputfile);
        BufferedWriter buffer = new BufferedWriter(file);
        for(String key : list_final.keySet()) {
            if(pos<n) {
                String salida = pos + ") " + key + ": " + "tf= " + list_tf.get(key) + " df= " + list_df.get(key) + " idflog10= " + list_idflog10.get(key) + " tfxidflog10= " + list_tfxidflog10.get(key)+"\n";
                buffer.write(salida);
            }
            pos++;
        }
    }

    /**
     * Método encargado del inicio de la ejecución del programa.
     * Se trata del método main (principal) del proyecto.
     * @param args Array con los parámetros recibidos desde el usuario
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // Variable initialization
        String usage = "java org.apache.lucene.BestTermsInDoc"
                + " [-index INDEX_PATH] [-docID D] [-field CAMPO] [-top N] [-order ORDER] [-outputfile OUTPUT_PATH]\n\n"
                + "This shows the best N terms in the document D of INDEX_PATH ordered by ORDER"
                + "it will be print or, optionally written in OUTPUT_PATH\n";

        String indexPath = null;
        String docId = null;
        int top = -1;
        String order = null;
        boolean output = false;

        String outputfile = null;
        String field = null;

        Map<String, Double> lista_final;
        assert false;
        Map<String, Double> lista_tf = new HashMap<>();
        Map<String, Double> lista_df = new HashMap<>();
        Map<String, Double> lista_idflog10 = new HashMap<>();
        Map<String, Double> lista_tfxidflog10 = new HashMap<>();


        // Loop in which we will obtain the parameters given by the user
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-docID":
                    docId = args[++i];
                    break;
                case  "-field":
                    field = args[++i];
                    break;
                case "-top":
                    top = Integer.parseInt(args[++i]);
                    break;
                case "-order":
                    order = args[++i];
                    break;
                case "-outputfile":
                    output = true;
                    outputfile = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }


        // Check that mandatory parameters exist
        if (indexPath == null || docId==null || field==null || top<0 || order==null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        if (output && outputfile==null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }


        // Open the IndexReader and perform the corresponding functions to obtain the output values
        Directory dir = FSDirectory.open(Paths.get(indexPath));

        int idDoc = Integer. parseInt(docId);
        try (IndexReader reader = DirectoryReader.open(dir)) {
            Map<String, Integer> terminos_mapeados = getTermFrequencies(reader, idDoc, field);
            Terms vector = reader.getTermVector(Integer.parseInt(docId), field);
            lista_tf = tf(terminos_mapeados);
            lista_df = df(terminos_mapeados,indexPath,field);
            lista_idflog10 = idflog10(terminos_mapeados, indexPath,field);
            lista_tfxidflog10 = tfxidflog10(terminos_mapeados, indexPath,field);
        } catch (IOException e) {
        System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        }


        // Switch to select the order to be used for the output
        switch (order){
            case "tf":
                lista_final = lista_tf;
                break;
            case "df":
                lista_final = lista_df;
                break;
            case  "idf":
                lista_final = lista_idflog10;
                break;
            case "tfxidf":
                lista_final = lista_tfxidflog10;
                break;
            default:
                throw new IllegalArgumentException("unknown parameter " + order);
        }


        // Output printed or written in a file, according to what the user asked for
        if(output){
            escribirsalida(lista_final,lista_tf,lista_df,lista_idflog10,lista_tfxidflog10,outputfile,top);
        }else {
            imprimirsalida(lista_final, lista_tf, lista_df, lista_idflog10, lista_tfxidflog10,top);
        }


    }

}
