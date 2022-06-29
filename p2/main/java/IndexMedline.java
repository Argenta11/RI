import java.io.*;
import java.nio.file.*;
import org.apache.lucene.demo.knn.DemoEmbeddings;
import org.apache.lucene.demo.knn.KnnVectorDict;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.IOUtils;

public class IndexMedline implements AutoCloseable{
    private final DemoEmbeddings demoEmbeddings;
    private final KnnVectorDict vectorDict;

    /**
     * This function initialize the KnnVectorDict
     * @param vectorDict Vector's new values
     * @throws IOException
     */
    private IndexMedline(KnnVectorDict vectorDict) throws IOException {
        if (vectorDict != null) {
            this.vectorDict = vectorDict;
            demoEmbeddings = new DemoEmbeddings(vectorDict);
        } else {
            this.vectorDict = null;
            demoEmbeddings = null;
        }
    }

    /**
     * This function obtains the Id of the document
     * @param linea Line with format ".I docId" where docId is the docId
     * @return      The Id of the document
     */
    private String obtenerId(String linea){
        StringBuilder Id= new StringBuilder();
        // Obtains the docId, coping the line without ".I "
        for(int a=3; a<linea.length();a++){
            Id.append(linea.charAt(a));
        }
        return Id.toString();
    }

    /**
     * This function recieves the file's path and parses it to obtain the documents. This documents are indexed.
     * @param Path      File's path in String format
     * @param writer    Indexwriter that writes in the index path given by user
     * @param path      File's path in Path format
     * @throws IOException
     */
    private void parsearArchivo (String Path, IndexWriter writer,Path path) throws IOException {

        // Variable initialization
        File doc = new File(Path);
        BufferedReader reader = new BufferedReader(new FileReader(doc));
        String linea;
        String docId="";
        StringBuilder contents= new StringBuilder();

        // Reading all lines
        while ((linea = reader.readLine()) != null){

            // If it's not a white line
            if(!linea.equals("")) {

                // If it begins with ".I", a new document starts and in this line we can obtain the id
                if (linea.charAt(0) == '.' && linea.charAt(1) == 'I') {

                    // Indexes the previous doc
                    if (!docId.equals("")) {
                        indexDoc(writer, path, contents.toString(), docId);
                    }
                    contents = new StringBuilder();
                    docId = obtenerId(linea);

                // If it begins with ".W", we jump this line, but the next lines will be the contents
                } else if (linea.charAt(0) == '.' && linea.charAt(1) == 'W') {

                // Until we have ".I", it will be part of contents
                } else {
                    contents.append(linea);
                    contents.append("\n");
                }
            }
        }
        // Indexing last document
        indexDoc(writer, path, contents.toString(), docId);
    }

    /**
     * Function to index one document
     * @param writer            Indexwriter that writes in the index path given by user
     * @param file              Path of the file where the document is
     * @param contentsStored    String that will be stored in the field "Contents"
     * @param docId             String that will be stored in the field "DocIDMedline"
     * @throws IOException
     */
    void indexDoc(IndexWriter writer, Path file, String contentsStored, String docId) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            // make a new, empty document
            Document doc = new Document();

            final FieldType fieldType = new FieldType(TextField.TYPE_STORED);
            fieldType.setStored(true);
            fieldType.setStoreTermVectors(true);

            //Add the contents of the file to a field named "contentsStored".
            doc.add(new StoredField("DocIDMedline", docId, fieldType));
            doc.add(new StoredField("Contents", contentsStored, fieldType));

            if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                // New index, so we just add the document (no old document can be there):
                System.out.println("adding " + file);
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                System.out.println("updating " + file);
                writer.updateDocument(new Term("path", file.toString()), doc);
            }
        }
    }

    /**
     * This method is responsible for starting the execution of the program.
     * It is the project's main method.
     * @param args Array with parameters added by the user
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // Variable initialization
        String usage = "java org.apache.lucene.IndexMedline"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-openmode OPENMODE] [-indexingmodel INDEXING_MODEL]\n\n"
                + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with SearchFiles\n"
                + "It can be selected creating, appending or both in OPENMODE"
                + "Indexing model possible values are jm lambda | tfidf";

        String index = null;
        String docs = null;
        boolean append = false;
        boolean create = false;
        boolean jm = false;
        float lambda = 0;

        // Obtain users parameters
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    index = args[++i];
                    break;
                case "-docs":
                    docs = args[++i];
                    break;
                case "-openmode":
                    switch (args[++i]) {
                        case "append":
                            append = true;
                            create = false;
                            break;

                        case "create":
                            create = true;
                            break;
                        case "create_or_append":
                            create = false;
                            break;
                        default:
                            throw new IllegalArgumentException("unknow parameter " + args[i]);
                    }
                    break;
                case "-indexingmodel":
                    switch (args[++i]) {
                        case "jm":
                            jm = true;
                            lambda = Float.parseFloat(args[++i]);
                            break;
                        case "tfidf":
                            break;
                        default:
                            throw new IllegalArgumentException("unknow parameter " + args[i]);
                    }
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        // Check correct parameters
        if (docs == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(docs);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath()
                    + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        if (jm && (lambda < 0 || lambda > 1)) {
            System.out.println("Lambda's value is not valid");
            System.exit(1);
        }

        KnnVectorDict vectorDictInstance = null;
        try {
            System.out.println("Indexing to directory '" + index + "'...");

            if (index == null) {
                System.err.println("Usage: " + usage);
                System.exit(1);
            }


            Directory dir = FSDirectory.open(Paths.get(index));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else if (append) {
                iwc.setOpenMode(OpenMode.APPEND);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            // Jelinek-Mercer smoothing or TFIDF can be chosen to set Similarity
            if (jm) {
                iwc.setSimilarity(new LMJelinekMercerSimilarity(lambda));
            } else {

                iwc.setSimilarity(new ClassicSimilarity());
            }

            // Create indexWriter and call to the function that parses the files and indexes the documents
            try (IndexWriter writer = new IndexWriter(dir, iwc); IndexMedline indexMedline = new IndexMedline(vectorDictInstance)) {
                indexMedline.parsearArchivo(docs, writer, docDir);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(vectorDictInstance);
        }
    }

    @Override
    public void close() throws Exception {
        IOUtils.close(vectorDict);
    }
}
