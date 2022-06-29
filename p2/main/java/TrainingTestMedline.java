import org.apache.lucene.queryparser.classic.ParseException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TrainingTestMedline {

    /**
     * Function that obtains the queries value for jm
     * @param   g1 Training queries
     * @param   g2 Test queries
     * @return  Array with queries values
     */
    private static int[] parsearevaljm(String g1, String g2){
        int[] resultado =new int[4];
        String[] a = g1.split("-");
        String[] b = g2.split("-");
        for (int i = 0; i<a.length; i++){
            resultado[i] = Integer.parseInt(a[i]);
            resultado[i+2] = Integer.parseInt(b[i]);
        }
        return resultado;
    }

    /**
     * Function that obtains the queries value for jm
     * @param   g1 Test queries
     * @return  Array with queries values
     */
    private static int[] parsearvalidf(String g1){
        String[] a = g1.split("-");
        int[] b = new int[2];
        for (int x=0;x<a.length;x++){
            b[x] = Integer.parseInt(a[x]);
        }
        return b;
    }

    /**
     * Function that obtains the average of list's values
     * @param valores   List with values
     * @return          The average of those values
     */
    private static double media(List<Double> valores){
        double sum=0;
        for (Double valore : valores) {
            sum += valore;
        }
        return sum/valores.size();
    }


    /**
     * This function obtain results of training with JM
     * @param metrica   metric chosen by user
     * @param pathname  index's path
     * @param n         value where ranking will be cut
     * @param g1        queries
     * @param valores   array with the starting querie and the ending querie
     * @return          It returns training results
     * @throws IOException
     * @throws ParseException
     */
    private static List<Double> entrenarjm(String metrica, String pathname, int n, String g1, int[] valores) throws IOException, ParseException {

        // Variable initialization
        List<Double> lista = new ArrayList<>();
        List<Double> medias = new ArrayList<>();

        // We obtain results for all values of lambda
        for (double i=0.1; i<=1.0; i+=0.1 ){

            // Creating SearchEvalMedline parameters
            String[] parametros = crear_parametros(true,i,pathname,String.valueOf(n),g1,valores[1],valores[0]);

            // Function that will return results of calling SearchEvalMedline
            List<Double> anadir = resultados(metrica, parametros);
            // Adding new values to the list
            lista.addAll(anadir);

            // Average will be added to another list
            medias.add(media(anadir));
        }

        // All averages will be at the end of the list
        lista.addAll(medias);
        return lista;
    }

    /**
     * It obtains the test results
     * @param metrica       Meter that will be used
     * @param parametros    SearchEvalMedline parameters
     * @return              It returns the test results
     * @throws IOException
     * @throws ParseException
     */
    private static List<Double> test(String metrica, String[] parametros) throws IOException, ParseException {
        List<Double> resultados = resultados(metrica, parametros);
        resultados.add(media(resultados));
        return resultados;
    }

    /**
     * Function that obtains results of SearchEvalMedline
     * @param metrica       Meter used
     * @param parametros    Parameters needed in SearchEvalMedline
     * @return              Returns the list of results
     * @throws IOException
     * @throws ParseException
     */
    private static List<Double> resultados(String metrica, String[] parametros) throws IOException, ParseException {
        // Variable initialization
        List<Double> resultados;
        SearchEvalMedline evalMedline = new SearchEvalMedline();

        // Cleaning SearchEvalMedline lists
        evalMedline.apnList.clear();
        evalMedline.recallList.clear();
        evalMedline.pnList.clear();

        // We call evalMedline.main() method
        evalMedline.main(parametros);

        // Choose the list of results acording to the meter
        if(Objects.equals(metrica, "P")){
            resultados = evalMedline.pnList;
        }else if(Objects.equals(metrica, "R")){
            resultados = evalMedline.recallList;
        }else{
            resultados = evalMedline.apnList;
        }
        return resultados;
    }

    /**
     * Function that creates SearchEvalMedline parameters
     * @param evaljm        Boolean that indicates if it's jm or tfidf
     * @param lambda        Lambda's value
     * @param pathname      Index's path
     * @param n             Ranking's cut
     * @param q1            Queries values
     * @param v2            Ending querie
     * @param v1            Starting querie
     * @return              It returns the array with parameters
     */
    static String[] crear_parametros(boolean evaljm, double lambda, String pathname, String n, String q1, int v2, int v1){

        // Variable initialization
        String[] parametros;
        int i=2;
        String[] parametros_comunes = new String[8];

        // If evaljm it will be added "-search jm lambda" but if tfidf it will be added "-search tfidf"
        if(evaljm) {
            parametros = new String[11];
            parametros[0]="-search";
            parametros[1] = "jm";
            parametros[2] = String.valueOf(lambda);
            i++;
        }else{
            parametros = new String[10];
            parametros[0]="-search";
            parametros[1] = "tfidf";
        }

        // All this parameters are added to both
        parametros_comunes[0] = "-indexin";
        parametros_comunes[1] = pathname;
        parametros_comunes[2] = "-cut";
        parametros_comunes[3] = n;
        parametros_comunes[4] = "-top";
        parametros_comunes[5] = String.valueOf(v2-v1);
        parametros_comunes[6] = "-queries";
        parametros_comunes[7] = q1;

        // Parameters are added to array
        for(int a=i;a<=(7+i);a++){
            parametros[a]=parametros_comunes[a-i];
        }

        return parametros;
    }

    /**
     * This function obtains with which alpha we have obtained better results
     * @param resultados_entrenamiento  Results of training
     * @param n                         Number of queries
     * @return                          It returns the best lambda
     */
    private static double obtener_mejor_lambda(List<Double> resultados_entrenamiento, int n){
        int mejor_media = 0;
        for(int i=0; i<10;i++) {
            if (resultados_entrenamiento.get(i+resultados_entrenamiento.size()-10) > resultados_entrenamiento.get(mejor_media+resultados_entrenamiento.size()-10)) {
                mejor_media = i;
            }
        }
        return 0.1*(mejor_media+1);
    }


    /**
     * Function that creates the csv file with results
     * @param nombre        File's name
     * @param datos         Useful facts
     * @param query_menor   Query in which results start
     * @param resultados    Results that will be added to the csv
     * @throws IOException
     */
    private static void obtener_csv(String nombre, String[] datos, int query_menor, List resultados) throws IOException {
        try{

            // Variable initialization
            FileWriter csvWriter = new FileWriter(nombre);

            // We call to different header and body functions acording to the type of results
            if(Objects.equals(datos[0], "entrenamiento")){
                cabecera_entrenamiento(csvWriter, datos);
                cuerpo_entrenamiento(csvWriter,resultados,query_menor);
            }else{
                cabecera_test(csvWriter,datos);
                cuerpo_test(csvWriter,resultados,query_menor);
            }

            csvWriter.flush();
            csvWriter.close();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * This function writes the header of a training's csv
     * @param csvWriter FileWriter that writes in this csv file
     * @param datos     Information that must be added
     * @throws IOException
     */
    private static void cabecera_entrenamiento (FileWriter csvWriter, String[] datos) throws IOException {
        // Writing the header
        String a = datos[1];
        csvWriter.append(a);
        System.out.print(a);

        // All lambda values
        for (int i=1; i<=9;i+=1){
            a = "0."+i;
            csvWriter.append(",\t");
            csvWriter.append(a);
            System.out.print(",\t"+a);
        }
        csvWriter.append(",\t1.0");
        System.out.print(",\t1.0");

        // Ending that row
        csvWriter.append("\n");
        System.out.print("\n");
    }

    /**
     * This function writes the header of a test's csv
     * @param csvWriter FileWriter that writes in this csv file
     * @param datos     Information that must be added
     * @throws IOException
     */
    private static void cabecera_test (FileWriter csvWriter, String[] datos) throws IOException{
        String a = datos[1];
        csvWriter.append(a);
        System.out.print(a);

        csvWriter.append(",\t");
        System.out.print(",\t");

        a=datos[2];
        csvWriter.append(a);
        System.out.print(a);

        // This ends the row
        csvWriter.append("\n");
        System.out.print("\n");
    }

    /**
     * This function writes the header of a training's csv
     * @param csvWriter     FileWriter that writes in this csv file
     * @param datos         Information that must be added
     * @param query_menor   First query
     * @throws IOException
     */
    private static void cuerpo_entrenamiento(FileWriter csvWriter, List<Double> datos, int query_menor) throws IOException {
        // Loop that writes all the data where values in a column have the same lambda's value and values in a row have the same query's value
        for(int a=0; a<(datos.size()-1)/10;a++){
            csvWriter.append(String.valueOf(query_menor+a));
            System.out.print(String.valueOf(query_menor+a));
            for(int i=0; i<10;i++){
                int y=i*(datos.size()-10)/10+a;
                String x =",\t"+datos.get(y);
                System.out.print(",\t"+datos.get(y));
                csvWriter.append(x);
            }
            csvWriter.append("\n");
            System.out.print("\n");
        }

        // Last row with averages
        csvWriter.append("Promedios");
        System.out.print("Promedios");
        for(int a=datos.size()-10; a<datos.size();a++){
            csvWriter.append(",\t");
            csvWriter.append(String.valueOf(datos.get(a)));
            System.out.print(",\t"+datos.get(a));
        }

        System.out.println();

    }

    /**
     * This function writes the header of a training's csv
     * @param csvWriter     FileWriter that writes in this csv file
     * @param resultados    Information that must be added
     * @param query_menor   First query
     * @throws IOException
     */
    private static void cuerpo_test(FileWriter csvWriter, List<Double> resultados, int query_menor) throws IOException {

        // In each row we add the query and the result that it had in the test
        for(int a=0; a<resultados.size()-1;a++ ){
            csvWriter.append(String.valueOf(query_menor+a));
            csvWriter.append(",\t");
            csvWriter.append(String.valueOf(resultados.get(a)));
            csvWriter.append("\n");
            System.out.print(query_menor+a+",\t"+resultados.get(a)+"\n");
        }

        // Last row is averages
        csvWriter.append("Promedios");
        csvWriter.append(",\t");
        csvWriter.append(String.valueOf(resultados.get(resultados.size()-1)));
        csvWriter.append("\n");
    }

    /**
     * This method is responsible for starting the execution of the program.
     * It is the project's main method.
     * @param args Array with parameters added by the user
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // Variable initialization
        String usage = "java org.apache.lucene.TestMedline"
                + " [-evaljm int1-int2 int3-int4 | -evaltfidf int3-int4]  [-cut N] [-metrica P | R | MAP] [-indexin PATHNAME]\n\n"
                + "Queries are thrown to the index located in PATHNAME:\n"
                + "in case '-evaljm', they are thrown with LM Jelinek-Mercer model between int1 and int2 "
                + "and then with best lambda values test queries between int3 and int4 are thrown\n"
                + "in case '-evalidf', they are thrown between int3 and int4\n"
                + "for both cases N is the ranking's cut and -metrica is the metric used";

        int n=0;
        String pathname = null;
        String g1 = null;
        String g2 = null;
        String metrica = null;
        int[] valores = new int[0];
        boolean evaljm = false;

        // Obtain users parameters
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-evaljm":
                    evaljm = true;
                    g1 = args[++i];
                    g2 = args[++i];
                    valores=parsearevaljm(g1, g2);
                    break;
                case "-evaltfidf":
                    evaljm = false;
                    g1 = args[++i];
                    valores = parsearvalidf(g1);
                    break;
                case "-cut":
                    n = Integer.parseInt(args[++i]);
                    break;
                case "-metrica":
                    metrica = args[++i];
                    break;
                case "-indexin":
                    pathname = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        // Check correct parameters
        if (pathname == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(pathname);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath()
                    + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        if (n<0){
            System.err.println("Cut value not valid");
            System.exit(1);
        }

        if(!Objects.equals(metrica, "P") && !Objects.equals(metrica, "R") && !Objects.equals(metrica, "MAP")){
            System.err.println("Metrica value not valid");
        }

        if((evaljm && valores.length!=4) || (!evaljm && valores.length!=2) || (g1 == null) || (evaljm && g2==null)){
            System.err.println("Queries value not valid");
            System.exit(1);
        }

        if(valores[0]>valores[1] || (evaljm && valores[2]>valores[3]) || valores[0]<0 || (evaljm && valores[2]<0)){
            System.err.println("Queries value not valid");
            System.exit(1);
        }

        List<Double> resultados_test;

        // Calling the corresponding methods acording to which option has been chosen
        if(evaljm){

            // We obtain training results
            List<Double> resultados_entrenamiento;
            resultados_entrenamiento=entrenarjm(metrica,pathname,n,g1, valores);

            // Creating training results' csv
            String nombre_archivo = "medline.jm.training."+g1+".test."+g2+"."+metrica+n+".training.csv";
            String[] datos = new String[3];
            datos[0] = "entrenamiento";
            datos[1] = metrica+"@"+n;
            obtener_csv(nombre_archivo,datos,valores[0],resultados_entrenamiento);

            // Obtain which lambda has had best results. We will use it in the test
            double mejor_lambda =obtener_mejor_lambda(resultados_entrenamiento,valores[1]-valores[0]);

            // We obtain test results
            String[] parametros = crear_parametros(true,mejor_lambda,pathname,String.valueOf(n),g2,valores[3],valores[2]);
            resultados_test = test(metrica,parametros);

            // Creating test results' csv
            datos[0] = "test";
            datos[1] = String.valueOf(mejor_lambda);
            datos[2] = metrica+"@"+n;
            nombre_archivo = "medline.jm.training."+g1+".test."+g2+"."+metrica+n+".test.csv";
            obtener_csv(nombre_archivo,datos,valores[2],resultados_test);

        }else{

            // In TFIDF there is no training, we obtain directly test results
            String[] parametros = crear_parametros(false,0.0,pathname,String.valueOf(n),g1,valores[1],valores[0]);
            resultados_test = test(metrica,parametros);

            // Creatin test results' csv
            String nombre_archivo= "medline.tfidf.training.null.test."+g1+"."+metrica+n+".test.csv";
            String[] datos= new String[3];
            datos[0]="test";
            datos[1]="";
            datos[2]= metrica+"@"+n;
            obtener_csv(nombre_archivo,datos,valores[0],resultados_test);
        }


    }
}