import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Objects;


public class WriteIndex {

    /**
     * Function that writes the index fields and their contents (for stored fields) to the document
     * @param writer    Printwriter that writes in the file whose path was given by the user
     * @param doc       Plain text file
     * @param i         Number that corresponds to the loop
     */
    private static void escribir (BufferedWriter writer, Document doc, int i) throws IOException {

        // Writer will write in the document a line for each field
        writer.write("Documento " + i +"\n");
        assert doc != null;
        writer.write("path = " + doc.get("path")+"\n");
        writer.write("contentsStored = " + doc.get("contentsStored")+"\n");
        System.out.println(doc.get("contentsStored"));
        writer.write("hostname = " + doc.get("hostname")+"\n");
        writer.write("thread = " + doc.get("thread")+"\n");
        writer.write("creationTime = " + doc.get("creationTime")+"\n");
        writer.write("lastAccessTime = " + doc.get("lastAccessTime")+"\n");
        writer.write("lastModifiedTime = " + doc.get("lastModifiedTime")+"\n");
        writer.write("creationTimeLucene = " + doc.get("creationTimeLucene")+"\n");
        writer.write("lastAccessTimeLucene  = " + doc.get("lastAccessTimeLucene")+"\n");
        writer.write("lastModifiedTimeLucene = " + doc.get("lastModifiedTimeLucene")+"\n");
        writer.write("\n");
        writer.flush();
    }

    /**
     * This method is responsible for starting the execution of the program.
     * It is the project's main method.
     * @param args Array with parameters added by the user
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // Variable initialization
        String usage = "java org.apache.lucene.WriteIndex"
                + " [-index INDEX_PATH] [-outputfile OUTPUT_PATH]\n\n"
                + "This dumps the index fields and their contents from INDEX_PATH, creating a plain text "
                + "in OUTPUT_PATH\n";

        String indexPath = null;
        String outputPath = null;
        DirectoryReader indexReader = null;
        Document doc = null;
        PrintWriter writer = null;

        // Loop in which we will obtain the parameters given by the user
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    break;
                case "-outputfile":
                    outputPath = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        // Check that mandatory parameters exist
        if (indexPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        if (outputPath == null){
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        // We create a writer assigned to the output file passed to us by the user
        writer = new PrintWriter(outputPath, StandardCharsets.UTF_8);

        // The index whose path the user indicated us is read
        try {
            Directory index = FSDirectory.open(Paths.get(indexPath));
            indexReader = DirectoryReader.open(index);
        } catch (IOException e1) {
            System.err.println("Graceful message: exception " + e1);
            e1.printStackTrace();
        }

        FileWriter file = new FileWriter(outputPath);
        BufferedWriter buffer = new BufferedWriter(file);

        // For each document in the folder we write the data
        for (int i = 0; i < Objects.requireNonNull(indexReader).numDocs(); i++) {

            try {
                doc = indexReader.document(i);
            } catch (IOException e1) {
                System.err.println("Graceful message: exception " + e1);
                e1.printStackTrace();
            }
            // Check that document is not null and calling the function that writes to the output
            assert doc != null;

            escribir(buffer,doc,i);
        }
    }
}
