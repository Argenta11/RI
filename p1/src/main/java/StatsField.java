import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.lang.annotation.Documented;
import java.nio.file.Paths;
import java.util.List;

public class StatsField {

    public static void main(String[] args) throws Exception {

        String usage = "java org.apache.lucene.StatsField"
                + " [-index INDEX_PATH] [-field FIELD]\n\n"
                + "This shows the statistics of the index in INDEX_PATH in that FIELD\n"
                + "IF FIELD is null it will show the statistics of all fields indexed";

        String indexPath = null;
        String field = null;

        for (int i = 0; i<args.length; i++){
            switch (args[i]){
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-field":
                    field = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if (indexPath == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        if (field != null){
            getStatisticsField(indexPath,field);
        } else{
            getStatisticsAllFields(indexPath);
        }

    }

    /**
     *
     * It is used to show statistics of a single field
     *
     * @param indexPath Path of index
     * @param field Name of the field we want to take statistics of.
     * @throws IOException
     */
    private static void getStatisticsField(String indexPath, String field) throws IOException {
        DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        IndexSearcher searcher = new IndexSearcher(reader);

        CollectionStatistics collectionStatistics = searcher.collectionStatistics(field);

        if (collectionStatistics != null) {
            System.out.println(collectionStatistics.toString());
        }
    }

    /**
     *
     * It is used to show statistics of a single field
     *
     * @param indexPath Path of index
     * @throws IOException
     */

    private static void getStatisticsAllFields(String indexPath) throws IOException {

        DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
        IndexSearcher searcher = new IndexSearcher(reader);
        CollectionStatistics collectionStatistics;

        // Creates a document to know all the fields of it.
        Document document = reader.document(0);
        List<IndexableField> allFields = document.getFields();

        // We iterate the list of fields and show the statistics of them
        for (IndexableField field: allFields){
            collectionStatistics = searcher.collectionStatistics(field.name());
            if (collectionStatistics != null) {
                System.out.println(collectionStatistics.toString());
            }
        }
    }

}