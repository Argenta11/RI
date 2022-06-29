import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import static org.apache.commons.math3.stat.inference.TestUtils.pairedTTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

public class Compare {

    /**
     * This function check that the file has a correct name
     * @param datos     Parts of the name to check
     * @return          Returns true if it's a valid file's name
     */
    private static boolean comprobaciones_individuales(String[] datos){
        if(!datos[0].equals("medline")){
            return false;
        }
        if(!datos[2].equals("training")){
            return false;
        }

        // In case jm, it will have training queries, in case tfidf it will be null
        if(datos[1].equals("jm")){
            if(!comprobar_valores(datos[3])){
                return false;
            }
        }else if(datos[1].equals("tfidf")){

            if (!Objects.equals(datos[3], "null")){

                return false;
            }
        }else{
            return false;
        }

        if(!Objects.equals(datos[4],"test")){
            return false;
        }

        if(!comprobar_valores(datos[5])){
            return false;
        }

        if(!datos[7].equals("test")){
            return false;
        }

        if(!comprobar_metrica(datos[6])){
            return false;
        }

        if(!datos[8].equals("csv")){
            return false;
        }

        return true;
    }

    /**
     * Funtion that check if values of a queries interval are correct
     * @param numeros   String with the interval
     * @return          It returns true if it is startingquerie-endingquerie
     */
    private static boolean comprobar_valores(String numeros){
        String[] valores= numeros.split("-");
        if(Integer.parseInt(valores[0])<0){
            return false;
        }
        return Integer.parseInt(valores[0]) < Integer.parseInt(valores[1]);
    }

    private static boolean comprobar_metrica(String valor){
        if(valor.toLowerCase().charAt(0)!='p' && valor.toLowerCase().charAt(0)!='r' && !valor.toLowerCase().startsWith("map")){
            return false;
        }

        if(valor.toLowerCase().startsWith("map")){
            if(Integer.parseInt(valor.substring(3))<0){
                return false;
            }
        }else{
            if(Integer.parseInt(valor.substring(1))<0){
                return false;
            }
        }
        return true;
    }

    /**
     * This function checks that both files are the same case
     * @param results1  Name of the first file
     * @param results2  Name of the second file
     * @return          Returns true if both files are the same search
     */
    private static boolean archivos_validos(String results1, String results2) {
        String[] datos1 =results1.split("\\.");
        String[] datos2 = results2.split("\\.");

        // Checking that both files have a valid name
        if(!comprobaciones_individuales(datos1) || !comprobaciones_individuales(datos2)){
            return false;
        }

        // Checking that queries intervals are the same
        if(!Objects.equals(datos1[5], datos2[5])){
            return false;
        }

        // Checking that both metrics are the same
        if(!Objects.equals(datos1[6],datos2[6])){
            return false;
        }
        return true;
    }

    /**
     * Function that checks if cell (0,0) is valid
     * @param a     Value of the celd
     * @return      It returns true if the value is valid
     */
    private static boolean probar_primera(String a){
        // In case it's jm, check if lambda's value is valid
        if(!Objects.equals(a, "")){
            return !(Double.parseDouble(a) <= 0 || Double.parseDouble(a) > 1);
        }
        return true;
    }

    /**
     * Check that content of the file is valid
     * @param path  File's path
     * @return      Returns true if the files is valid
     */
    private static boolean comprobar_estructura(String path){

        // Reading the csv file
        try (CSVReader reader = new CSVReader(new FileReader(path))) {
            // List of arrays with all the values of a row
            List<String[]> r = reader.readAll();
            // There must be two columns
            for (String[] arrays : r) {
                if(arrays.length!=2){
                    return false;
                }
            }
            // Check if the cell (0,0) is valid
            if(!probar_primera(r.get(0)[0])){
                return false;
            }
            int x= Integer.parseInt(r.get(1)[0]);
            int a=-1;

            for(String[] arrays: r){
                // Check if all queries number are correct
                if(a!=-1 && a!=r.size()-2 && Integer.parseInt(arrays[0])!=x+a){
                    return false;
                }
                // Check if all the values are correct
                if(a!=-1 && (Double.parseDouble(arrays[1])>1 || Double.parseDouble(arrays[1])<0)){
                    return false;
                }
                a++;
            }

            String[] datos = path.split("\\.");
            String[] met = r.get(0)[1].split("@");
            String comparar = met[0].toLowerCase()+met[1];

            // Returns true if both metrics are the same
            return Objects.equals(datos[6].toLowerCase(), comparar.toLowerCase().substring(1));

        } catch (IOException | CsvException e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * This method is responsible for starting the execution of the program.
     * It is the project's main method.
     * @param args Array with parameters added by the user
     * @throws Exception
     */
    public static void main(String[] args) {

        // Variable initialization
        String usage = "java org.apache.lucene.Compare"
                + " [-results result1 result2] [-test t|wilcoxon alpha]\n\n"
                + "This makes a significance test to result1 and result2,"
                + "two different results to the same queries\n"
                + "The test used can be statics -t-test or Wilcoxon."
                + "The level of significance is alpha.";


        String results1 = null;
        String results2 = null;
        String test = null;
        double alpha = -1.0;

        // Obtain users parameters
        for (int i = 0; i< args.length;i++){
            switch (args[i]){
                case "-results":
                    results1 = args[++i];
                    results2 = args[++i];
                    break;
                case "-test":
                    test = args[++i];
                    alpha = Double.parseDouble(args[++i]);
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        // Check correct parameters
        if(results1==null || results2==null || test==null || alpha==-1.0){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        if(!test.equals("t") && !test.equals("wilcoxon")){
            System.err.println("Test value not valid");
            System.exit(1);
        }

        if(!archivos_validos(results1,results2)){
            System.err.println("Files are not valid");
            System.exit(1);
        }

        if(!comprobar_estructura(results1) || !comprobar_estructura(results2)){
            System.err.println("Files structure is not valid");
            System.exit(1);
        }

        // Reads both files
        try (CSVReader reader = new CSVReader(new FileReader(results1))) {
            try(CSVReader reader2 = new CSVReader(new FileReader(results2))){

            // Variable initialization
            List<String[]> doc1 = reader.readAll();
            List<String[]> doc2 = reader2.readAll();
            double[] sample1 = new double[doc1.size()];
            double[] sample2 = new double[doc1.size()];

            // Obtain values for both documents
            for(int i=1; i<doc1.size()-1;i++){
                sample1[i]=Double.parseDouble(doc1.get(i)[1]);
                sample2[i]=Double.parseDouble(doc2.get(i)[1]);
            }

            double pValor;
            boolean resultado_test;

            // Comparing
            if (test.equals("t")) {
                pValor =pairedTTest(sample1, sample2);
                resultado_test = pairedTTest(sample1 , sample2, alpha);
            } else {
                pValor =new WilcoxonSignedRankTest().wilcoxonSignedRankTest(sample1, sample2, false);
                resultado_test = pValor<alpha;
            }

            // Printing results
            System.out.print("Resultado del test: ");
            if(resultado_test){
                System.out.println("Se rechaza la hipótesis nula");
            }else{
                System.out.println("La hipótesis nula no se puede rechazar");
            }
            System.out.println("El pValor es: " + pValor);

            } catch (CsvException e) {
                e.printStackTrace();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}
