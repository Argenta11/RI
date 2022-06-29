/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.demo.knn.DemoEmbeddings;
import org.apache.lucene.demo.knn.KnnVectorDict;
import org.apache.lucene.document.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.IOUtils;

/**
 * Index all text files under a directory.
 *
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing. Run
 * it with no command-line arguments for usage information.
 */
public class IndexFiles implements AutoCloseable {
	static final String KNN_DICT = "knn-dict";

	// Calculates embedding vectors for KnnVector search
	private final DemoEmbeddings demoEmbeddings;
	private final KnnVectorDict vectorDict;
	private final Properties properties = new Properties();

	private IndexFiles(KnnVectorDict vectorDict) throws IOException {
		try (OutputStream outputStream = new FileOutputStream("src/main/resources/config.properties")){
			properties.setProperty("onlyFiles", ".java .c .txt .odt .doc .pdf");
			properties.setProperty("onlyTopLines","12");
			properties.setProperty("onlyBottomLines","2");
			properties.store(outputStream,null);

		}
		if (vectorDict != null) {
			this.vectorDict = vectorDict;
			demoEmbeddings = new DemoEmbeddings(vectorDict);
		} else {
			this.vectorDict = null;
			demoEmbeddings = null;
		}
	}

	public static class WorkerThread implements Runnable {
		private final IndexWriter indexWriter;
		private final IndexFiles indexFiles;
		private final Path folder;
		private final int deep;

		public WorkerThread(IndexWriter indexWriter, IndexFiles indexFiles, Path folder, int deep) {
			this.indexWriter = indexWriter;
			this.indexFiles = indexFiles;
			this.folder = folder;
			this.deep = deep;
		}

		@Override
		public void run() {
			try{
				System.out.println(String.format("I am the thread '%s' and I am responsible for folder '%s'",
						Thread.currentThread().getName(), folder));
				indexFiles.indexDocs(indexWriter, folder, deep);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}


	/** Index all text files under a directory. */
	public static void main(String[] args) throws Exception {
		String usage = "java org.apache.lucene.IndexFiles"
				+ " [-index INDEX_PATH] [-docs DOCS_PATH] [-update] [-knn_dict DICT_PATH]\n\n"
				+ "This indexes the documents in DOCS_PATH, creating a Lucene index"
				+ "in INDEX_PATH that can be searched with SearchFiles\n"
				+ "IF DICT_PATH contains a KnnVector dictionary, the index will also support KnnVector search";
		String indexPath = "index";
		String docsPath = null;
		String vectorDictSource = null;
		boolean create = true;
		boolean append = false;
		//numThreads is initially the number of processors available to JAVA
		int numThreads = Runtime.getRuntime().availableProcessors();
		//deep is initially the maximum integer number, so we do not have any problems with the maximum depth
		int deep = Integer.MAX_VALUE;


		boolean partialIndexes = false;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-index":
					indexPath = args[++i];
					break;
				case "-docs":
					docsPath = args[++i];
					break;
				case "-knn_dict":
					vectorDictSource = args[++i];
					break;
				case "-update":
					create = false;
					break;
				case "-create":
					create = true;
					break;
				case "-openmode":
					switch (args[++i]){
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
				case "-numThreads":
					numThreads = Integer.parseInt(args[++i]);
					break;
				case "-deep":
					deep = Integer.parseInt(args[++i]);
					break;
				case "-partialIndexes":
					partialIndexes = true;
					break;
				default:
					throw new IllegalArgumentException("unknown parameter " + args[i]);
			}
		}

		//docsPath cannot be null. IndexPath could be null, so we would create a directory named index
		if (docsPath == null) {
			System.err.println("Usage: " + usage);
			System.exit(1);
		}

		final Path docDir = Paths.get(docsPath);
		if (!Files.isReadable(docDir)) {
			System.out.println("Document directory '" + docDir.toAbsolutePath()
					+ "' does not exist or is not readable, please check the path");
			System.exit(1);
		}

		Date start = new Date();
		try {
			System.out.println("Indexing to directory '" + indexPath + "'...");

			Directory dir = FSDirectory.open(Paths.get(indexPath));
			Analyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

			if(partialIndexes){
				iwc.setOpenMode(OpenMode.CREATE);
			}else {
				if (create) {
					// Create a new index in the directory, removing any
					// previously indexed documents:
					iwc.setOpenMode(OpenMode.CREATE);
				} else if (append){
					iwc.setOpenMode(OpenMode.APPEND);
				} else {
					// Add new documents to an existing index:
					iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
				}
			}



			// Optional: for better indexing performance, if you
			// are indexing many documents, increase the RAM
			// buffer. But if you do this, increase the max heap
			// size to the JVM (eg add -Xmx512m or -Xmx1g):
			//
			// iwc.setRAMBufferSizeMB(256.0);

			KnnVectorDict vectorDictInstance = null;
			long vectorDictSize = 0;
			if (vectorDictSource != null) {
				KnnVectorDict.build(Paths.get(vectorDictSource), dir, KNN_DICT);
				vectorDictInstance = new KnnVectorDict(dir, KNN_DICT);
				vectorDictSize = vectorDictInstance.ramBytesUsed();
			}


			//Creates a pool of n threads
			final ExecutorService executor = Executors.newFixedThreadPool(numThreads);

			IndexWriter writer = new IndexWriter(dir, iwc);

			List<IndexWriter> indexWriterList = new ArrayList<>();
			Runnable worker;

			try (IndexFiles indexFiles = new IndexFiles(vectorDictInstance);
				 DirectoryStream<Path> directoryStream = Files.newDirectoryStream(docDir)) {

				for (final Path path : directoryStream) {
					if (Files.isDirectory(path)) {

						if(partialIndexes){
							Directory partialDirectories = FSDirectory.open(Path.of(indexPath + "/index_" + new File(String.valueOf(path)).getName()));
							IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);

							if (create) {
								// Create a new index in the directory, removing any
								// previously indexed documents:
								indexWriterConfig.setOpenMode(OpenMode.CREATE);
							} else if (append){
								indexWriterConfig.setOpenMode(OpenMode.APPEND);
							} else {
								// Add new documents to an existing index:
								indexWriterConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
							}

							IndexWriter indexWriterPartial = new IndexWriter(partialDirectories, indexWriterConfig);
							indexWriterList.add(indexWriterPartial);

							worker = new WorkerThread(indexWriterPartial, indexFiles, path, deep);
						}
						else{
							worker = new WorkerThread(writer, indexFiles, path, deep);

						}
						/* We process each subfolder in a new thread. */

						/*
						 * Send the thread to the ThreadPool. It will be processed eventually.
						 */
						executor.execute(worker);
					}
				}

				// NOTE: if you want to maximize search performance,
				// you can optionally call forceMerge here. This can be
				// a terribly costly operation, so generally it's only
				// worth it when your index is relatively static (ie
				// you're done adding documents to it):
				//
				// writer.forceMerge(1);
			} catch (final IOException e) {
				e.printStackTrace();
				System.exit(-1);
			} finally {
				IOUtils.close(vectorDictInstance);
			}

			/*
			 * Close the ThreadPool; no more jobs will be accepted, but all the previously
			 * submitted jobs will be processed.
			 */
			executor.shutdown();

			/* Wait up to 1 hour to finish all the previously submitted jobs */
			try {
				executor.awaitTermination(1, TimeUnit.HOURS);
			} catch (final InterruptedException e) {
				e.printStackTrace();
				System.exit(-2);
			}


			System.out.println("Finished all threads");
			for(IndexWriter indexWriter : indexWriterList){
				indexWriter.commit();
				indexWriter.close();
			}

			for(IndexWriter indexWriter: indexWriterList){
				writer.addIndexes(indexWriter.getDirectory());
			}

			writer.commit();
			writer.close();



			Date end = new Date();
			try (IndexReader reader = DirectoryReader.open(dir)) {
				System.out.println("Indexed " + reader.numDocs() + " documents in " + (end.getTime() - start.getTime())
						+ " milliseconds");
			}
		} catch (IOException e) {
			System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
		}
	}

	/**
	 * Indexes the given file using the given writer, or if a directory is given,
	 * recurses over files and directories found under the given directory.
	 *
	 * <p>
	 * NOTE: This method indexes one document per input file. This is slow. For good
	 * throughput, put multiple documents into your input file(s). An example of
	 * this is in the benchmark module, which can create "line doc" files, one
	 * document per line, using the <a href=
	 * "../../../../../contrib-benchmark/org/apache/lucene/benchmark/byTask/tasks/WriteLineDocTask.html"
	 * >WriteLineDocTask</a>.
	 *
	 * @param writer Writer to the index where the given file/dir info will be
	 *               stored
	 * @param path   The file to index, or the directory to recurse into to find
	 *               files to indt
	 * @throws IOException If there is a low-level I/O error
	 */
	void indexDocs(final IndexWriter writer, Path path, int deep) throws IOException {
		if (Files.isDirectory(path)) {
			Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), deep, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {

					//Finds out if there is a property 'onlyfiles'. If that occurs it creates a vector of strings with the types of field. Ex ['.c','.txt','.java']
					if(properties.getProperty("onlyFiles") != null){
						String[] var_onlyFiles = properties.getProperty("onlyFiles").split(" ");

						//Iterates the strings' vector, and we compare if the file finishes in that extension.
						//If it occurs the file is indexed, and in the other case we don't index it.
						for(String type : var_onlyFiles){
							if (file.toString().endsWith(type)){
								try {
									indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
								} catch (@SuppressWarnings("unused") IOException ignore) {
									ignore.printStackTrace(System.err);
									// don't index files that can't be read.
								}
							}

						}
					}
					//If there is not, it will index all the files which are not directories
					else {

						try {
							if(!Files.isDirectory(file)){
								indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
							}
						} catch (@SuppressWarnings("unused") IOException ignore) {
							ignore.printStackTrace(System.err);
							// don't index files that can't be read.
						}
					}
					return FileVisitResult.CONTINUE;
				}


			});
		} else {
			indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
		}
	}

	/** Indexes a single document */
	void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
		try (InputStream stream = Files.newInputStream(file)) {
			// make a new, empty document
			Document doc = new Document();

			// Add the path of the file as a field named "path". Use a
			// field that is indexed (i.e. searchable), but don't tokenize
			// the field into separate words and don't index term frequency
			// or positional information:
			Field pathField = new StringField("path", file.toString(), Field.Store.YES);
			doc.add(pathField);

			// Allow to store basic file attributes.
			BasicFileAttributes fileAttributes = Files.readAttributes(file, BasicFileAttributes.class);

			// Add the last modified date of the file a field named "modified".
			// Use a LongPoint that is indexed (i.e. efficiently filterable with
			// PointRangeQuery). This indexes to milli-second resolution, which
			// is often too fine. You could instead create a number based on
			// year/month/day/hour/minutes/seconds, down the resolution you require.
			// For example the long value 2011021714 would mean
			// February 17, 2011, 2-3 PM.
			doc.add(new LongPoint("modified", lastModified));




			// Stores the contents that it is necessary, taking into account the value of onlyTopLines and onlyBottomLines
			String contentsStored = onlyLines(stream);

			// Add the contents of the file to a field named "contents". Specify a Reader,
			// so that the text of the file is tokenized and indexed, but not stored.
			// Note that FileReader expects the file to be in UTF-8 encoding.
			// If that's not the case searching for special characters will fail.
			doc.add(new TextField("contents",
					new BufferedReader(new InputStreamReader(new ByteArrayInputStream(contentsStored.getBytes()), StandardCharsets.UTF_8))));




			final FieldType fieldType = new FieldType(TextField.TYPE_STORED);
			fieldType.setStored(true);
			fieldType.setStoreTermVectors(true);

			//Add the contents of the file to a field named "contentsStored".

			doc.add(new StoredField("contentsStored", contentsStored, fieldType));

			//Add the host's identification in charged of indexing the file to a field named "hostname".

			doc.add(new TextField("hostname", InetAddress.getLocalHost().getHostName(), Field.Store.NO));

			//Add the thread's identification in charged of indexing the file to a field named "thread".

			doc.add(new TextField("thread", Thread.currentThread().getName(), Field.Store.NO));

			//Add the file's type to a field named "type".

			doc.add(new TextField("type", getType(fileAttributes), Field.Store.NO));

			//Add the file's size in KiloBytes to a field named "sizeKB".

			doc.add(new LongPoint("sizeKB", getSize(fileAttributes)));

			//Add the file's creation time to a field named "creationTime".

			doc.add(new TextField("creationTime", fileAttributes.creationTime().toString(), Field.Store.NO));

			//Add the file's last access time to a field named "lastAccessTime".

			doc.add(new TextField("lastAccessTime", fileAttributes.lastAccessTime().toString(), Field.Store.NO));

			//Add the file's last modified time to a field named "lastModifiedTime".

			doc.add(new TextField("lastModifiedTime", fileAttributes.lastModifiedTime().toString(), Field.Store.NO));

			//Add the file's creation time to a field named "creationTimeLucene" in Lucene's format.

			doc.add(new TextField("creationTimeLucene", DateTools.dateToString(getDateCreationTime(fileAttributes), DateTools.Resolution.SECOND), Field.Store.NO));

			//Add the file's last access time to a field named "lastAccessTimeLucene" in Lucene's format.

			doc.add(new TextField("lastAccessTimeLucene", DateTools.dateToString(getDateLastAccessTime(fileAttributes), DateTools.Resolution.SECOND),Field.Store.NO));

			//Add the file's last modified time to a field named "lastModifiedTimeLucene" in Lucene's format.

			doc.add(new TextField("lastModifiedTimeLucene", DateTools.dateToString(getDateLastModifiedTime(fileAttributes),DateTools.Resolution.SECOND),Field.Store.NO));


			if (demoEmbeddings != null) {
				try (InputStream in = Files.newInputStream(file)) {
					float[] vector = demoEmbeddings
							.computeEmbedding(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
					doc.add(new KnnVectorField("contents-vector", vector, VectorSimilarityFunction.DOT_PRODUCT));
				}
			}


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
	 *
	 * @param stream It represents an input stream of bytes. These bytes is the content of the file
	 * @return It returns a string with the content of the field that we are going to index
	 * @throws IOException
	 */
	private String onlyLines(InputStream stream) throws IOException {

		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
		StringBuilder contentsBuilder = new StringBuilder();
		String contents = "";

		//Initialize numeric variables
		int var_onlyTopLines = Integer.MAX_VALUE;
		int var_onlyBottomLines = Integer.MIN_VALUE;
		//Initialize boolean variables to know if the numeric variables have changed
		boolean topLines = false;
		boolean bottomLines = false;

		//We take the value of the property onlyTopLines and onlyBottomLines in the case they have been initialized
		if(properties.getProperty("onlyTopLines") != null){
			var_onlyTopLines = Integer.parseInt(properties.getProperty("onlyTopLines"));
		}
		if(properties.getProperty("onlyBottomLines") != null){
			var_onlyBottomLines = Integer.parseInt(properties.getProperty("onlyBottomLines"));
		}

		// If the numeric variables have not changed, it means there is not any property like that.
		// In that case we collect all lines
		if(var_onlyTopLines == Integer.MAX_VALUE && var_onlyBottomLines == Integer.MIN_VALUE){
			contents = bufferedReader.lines().collect(Collectors.joining("\n"));
		} else {
			// We need a list to insert the lines that we are going to store
			List list = bufferedReader.lines().collect(Collectors.toList());
			// It represents the size of the file in terms of lines
			int numLines = list.size();

			// We change the initial value of bottomLines if it has changed.
			// We also compare that value with the number of lines.
			// In the case it is bigger, we would change that value to the maximum (number of lines)
			if(var_onlyBottomLines!=Integer.MIN_VALUE){
				if(var_onlyBottomLines>numLines){
					var_onlyBottomLines=numLines;
				}
				bottomLines = true;
			}

			// We change the initial value of topLines if it has changed.
			// We also compare that value with the number of lines.
			// In the case it is bigger, we would change that value to the maximum (number of lines)
			if(var_onlyTopLines!=Integer.MAX_VALUE){
				if (var_onlyTopLines>numLines){
					var_onlyTopLines = numLines;
				}
				topLines = true;
			}

			// If one of those variables is equal to the number of lines, all the content will be stored.
			// So we only need one iteration
			if(var_onlyBottomLines == numLines){
				topLines = false;
			} else if(var_onlyTopLines == numLines){
				bottomLines = false;
			}

			// If the sum of both variables is more than the number of lines we are going to store all the content.
			// We hange the value of both numeric varibales
			if(topLines && bottomLines && var_onlyBottomLines+var_onlyTopLines > numLines){
				var_onlyBottomLines = numLines;
				var_onlyTopLines = 0;
			}

			// If bottomLines has changed, we iterate until the value of
			// the bottom lines we want to store
			if(bottomLines){
				for(int i = 0; i<var_onlyBottomLines; i++){
					contentsBuilder.append(list.get(i)).append(" ");
				}
			}
			// If topLines has changed, we iterate from the number of lines
			// minus the value of the top lines we want to store.
			if(topLines){
				for (int i = list.size()-var_onlyTopLines; i<list.size();i++){
					contentsBuilder.append(list.get(i)).append(" ");
				}
			}
			// Converts the StringBuilder to a String
			contents = contentsBuilder.toString();

		}

		return contents;
	}

	/**
	 *
	 * Method to know the file type we are working with.
	 * 	 * We work with 4 types:
	 * 	 *         - Regular File
	 * 	 *         - Directory
	 * 	 *         - Symbolic link
	 * 	 *         - Otro
	 *
	 * @param fileAttributes Basic attributes associated to the file
	 * @return String which is the type of file we are working with
	 */
	private String getType(BasicFileAttributes fileAttributes){
		if (fileAttributes.isRegularFile()){
			return "regular file";
		} else if (fileAttributes.isDirectory()){
			return "directory";
		} else if (fileAttributes.isSymbolicLink()){
			return "symbolic link";
		} else
			return "otro";
	}

	/**
	 *
	 * Method to know file's size in KiloBytes
	 *
	 * @param fileAttributes Basic attributes associated to the file
	 * @return Value of file's size in KB
	 */
	private Long getSize(BasicFileAttributes fileAttributes){
		//BasicFileAtrributes.size() return file's size in bytes
		//Convert bytes in kilobytes x/1024
		return (fileAttributes.size())/1024;
	}

	/**
	 *
	 * Method to know the date of creation time in Lucene form
	 *
	 * @param fileAttributes Basic attributes associated to the file
	 * @return Date of creation time in Date format
	 */
	private Date getDateCreationTime(BasicFileAttributes fileAttributes){
		LocalDate a = fileAttributes.creationTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return Date.from(a.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	/**
	 *
	 * Method to know the date of last modified time in Lucene form
	 *
	 * @param fileAttributes Basic attributes associated to the file
	 * @return Date of last modified time in Date format
	 */
	private Date getDateLastModifiedTime(BasicFileAttributes fileAttributes){
		LocalDate a = fileAttributes.lastModifiedTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return Date.from(a.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}

	/*
	 * It returns it in Date format
	 */

	/**
	 *
	 * Method to know the date of last access time in Lucene form
	 *
	 * @param fileAttributes Basic attributes associated to the file
	 * @return Date of last access time in Date format
	 */
	private Date getDateLastAccessTime(BasicFileAttributes fileAttributes){
		LocalDate a = fileAttributes.lastAccessTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		return Date.from(a.atStartOfDay(ZoneId.systemDefault()).toInstant());
	}


	@Override
	public void close() throws IOException {
		IOUtils.close(vectorDict);
	}
}
