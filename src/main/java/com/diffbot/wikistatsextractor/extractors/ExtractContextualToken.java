package com.diffbot.wikistatsextractor.extractors;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;

import com.diffbot.wikistatsextractor.dumpparser.DumpParser;
import com.diffbot.wikistatsextractor.util.Util;

/**
 * extract for each dbpedia entry, the list of tokens that you can find around
 * it in each paragraph.
 * 
 * This class is quite complex since it uses 3 DumpParser.Worker.
 * If you don't know how that works, I suggest to have a look at DumpParser first
 * 
 * @author sam
 */
public class ExtractContextualToken {
	public static int MIN_NB_CONTEXTS = 2;
	
	public static int MAX_LENGTH_PARAGRAPH=5000;
	public static int MAX_LENGTH_SF=80;
	public static int MIN_LENGTH_SF=2;
	public static int MAX_NB_TOKEN_SF=4;
	public static String LANGUAGE="en";
	public static String ANAYZER_NAME="en.EnglishAnalyzer";

	/** first worker. Given a page, it extract token from each paragraph.
	 *  Associate each paragraph with a unique id. 
	 *  Store those paragraph in the output. 
	 *  Keep track of all paragraphs spanned by a Uri.
	 * @author sam
	 *
	 */
	public static class ECTWorker extends DumpParser.Worker {
		static int paragraphHash = -1;

		public static synchronized int getNewHash() {
			paragraphHash++;
			return paragraphHash;
		}
		
		
		
		

		/* associate for each resource a list of paragraphs in which they appear */
		ConcurrentHashMap<String, List<Integer>> paragraphe_per_resource;
		/* contains all the existing Uris */
		Set<String> existing_uris;
		/* contains the redirections */
		HashMap<String, String> redirections;

		protected Analyzer analyzer;

		public ECTWorker(CharArraySet stopwords, String analyzer_name, ConcurrentHashMap<String, List<Integer>> paragraphe_per_resource,
				Set<String> existing_uris, HashMap<String, String> redirections) {
			String analyzer_full_name = "org.apache.lucene.analysis." + analyzer_name;
			try {
				analyzer = (Analyzer) Class.forName(analyzer_full_name).getConstructor(CharArraySet.class).newInstance(stopwords);
			} catch (Exception e) {
				e.printStackTrace();
			}
			this.paragraphe_per_resource = paragraphe_per_resource;
			this.existing_uris = existing_uris;
			this.redirections = redirections;

		}

		@Override
		public void doSomethingWithPage(String page) {
			/**
			 * get the paragraphs from the page, get rids of the lists, the
			 * reference, but let the links
			 */

			List<String> paragraphs = Util.getCleanTextFromPage(page, true, true, false,true);

			if (paragraphs != null) {

				
				for (String text_paragraph : paragraphs) {
					
					StringBuilder sb = new StringBuilder();
					
					// a paragraphe that is more than 5000 chars? Bullshit.
					if (text_paragraph.length()>MAX_LENGTH_PARAGRAPH)
						continue;

					/** look in the paragraph for any links */
					List<Util.PairUriSF> surface_forms = Util.getSurfaceFormsInString(text_paragraph, MAX_LENGTH_SF,
							MIN_LENGTH_SF, MAX_NB_TOKEN_SF, LANGUAGE);

					/**
					 * if we haven't found any surface form, this paragraph is
					 * useless and we don't anaylze it
					 */
					if (surface_forms == null) {
						continue;
					}

					/**
					 * here we try to find the actual uri. by going through all
					 * the redirections
					 */
					for (Util.PairUriSF pusf : surface_forms) {
						boolean has_redirection = true;
						int counter = 0;
						while (has_redirection) {
							counter++;
							if (counter > 15) {
								System.out.println("too many redirections: " + pusf.surface_form);
								break;
							}
							String uperified = Util.upperifyFirstChar(pusf.uri);
							String redirect_uri = redirections.get(pusf.uri);
							if (redirect_uri != null) {
								pusf.uri = redirect_uri;
							} else {
								String redirect_uri2 = redirections.get(uperified);
								if (redirect_uri2 != null) {
									pusf.uri = redirect_uri2;
								} else {
									has_redirection = false;
								}
							}
						}
					}
					
					/** all right, last check, does the uri exist. If not, we remove it from the list */
					for (int i=surface_forms.size()-1; i>=0; i--){
						Util.PairUriSF pusf=surface_forms.get(i);
						pusf.uri=Util.upperifyFirstChar(pusf.uri);
						String escaped_uri=Util.escapeWiki(pusf.uri);
						if (!existing_uris.contains(escaped_uri))
							surface_forms.remove(i);
					}
					
					if (surface_forms.size()==0) {
						continue;
					}

					

					/** get the Hashcode of the paragraph */
					int hash = ECTWorker.getNewHash();
					sb.append(hash);

					/**
					 * now that have extracted all the surface forms, we can
					 * clean them in the text
					 */
					String clean_paragraph_text = Util.cleanSurfaceForms(text_paragraph);

					/** tokenize here */
					try {
						TokenStream stream = analyzer.tokenStream("paragraph", clean_paragraph_text);
						stream.reset();
						while (stream.incrementToken()) {
							String token = stream.getAttribute(CharTermAttribute.class).toString();
							sb.append(',');
							sb.append(',');
							sb.append(token);
						}
						sb.append('\n');
						stream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					writeInOutput(sb.toString());

					/** add the surface forms to the collection */
					for (Util.PairUriSF sf : surface_forms) {
						List<Integer> paragraph_list = paragraphe_per_resource.get(Util.upperifyFirstChar(sf.uri));
						if (paragraph_list != null) {
							synchronized (paragraph_list) {
								paragraph_list.add(hash);
							}
						} else {
							paragraph_list = new ArrayList<Integer>();
							paragraph_list.add(hash);
							paragraphe_per_resource.put(sf.uri, paragraph_list);
						}
					}
				}

			}

		}
	}
	
	
	/** second worker, this one is a bit simpler. 
	 *  It will store all the token contained in the paragraph in an efficient form
	 *  int[][] voc.
	 *  where voc[1] is an array containing all the token from paragraph with id 1.
	 *  each token is stored as an integer, and the HashMaps  
	 * @author sam
	 *
	 */
	public static class VocabularyBuilderWorker extends DumpParser.Worker {
		private static int hash=-1;
		public static synchronized int getNewHash(){
			hash++;
			return hash;
		}
		public static synchronized int getHashRank(){
			return hash;
		}
		ConcurrentHashMap<String, Integer> vocabulary_one_way;
		ConcurrentHashMap<Integer, String> vocabulary_other_way;
		int[][] paragraphs;
		
		VocabularyBuilderWorker(ConcurrentHashMap<String, Integer> vocabulary_one_way, 
				ConcurrentHashMap<Integer, String> vocabulary_other_way,
				int[][] paragraphs){
			this.vocabulary_one_way=vocabulary_one_way;
			this.vocabulary_other_way=vocabulary_other_way;
			this.paragraphs=paragraphs;
		}
		
		@Override
		public void doSomethingWithPage(String page) {
			// the page in question contains multiple lines
			String[] each_line=page.split("\n");
			for (String line: each_line){
				String[] split = Util.fastSplit(line);
				if (split.length > 1) {
					Integer indice_paragraph = Integer.parseInt(split[0]);
					int[] words_in_paragraph = new int[split.length - 1];
					for (int i = 1; i < split.length; i++) {
						Integer indice_word_in_voc = vocabulary_one_way.get(split[i]);
						if (indice_word_in_voc == null) {
							// we add this word to the vocabulary
							int hash=getNewHash();
							vocabulary_one_way.put(split[i], hash);
							vocabulary_other_way.put(hash, split[i]);
							indice_word_in_voc = hash;
						}
						words_in_paragraph[i - 1] = indice_word_in_voc;
					}
					paragraphs[indice_paragraph] = words_in_paragraph;
				}
			}
		}
	}
	
	/** The third and last Worker. 
	 *  given the vocabulary and content of the paragraphs, it reads 
	 *  the file tmp_references, which contains the list of paragraphs id associated with 
	 *  each uri. 
	 *  build (in output) a file that associates URI with the token
	 * @author sam
	 *
	 */
	public static class ContextBuilder extends DumpParser.Worker {
		private static int hash=-1;
		
		public static class TokenCount{
			String token;
			int count;
		}
		
		public static class CompTokenCount implements Comparator<TokenCount>{
			@Override
			public int compare(TokenCount arg0, TokenCount arg1) {
				return -Integer.compare(arg0.count, arg1.count);
			}
			
		}
		public static synchronized int getNewHash(){
			hash++;
			return hash;
		}
		ConcurrentHashMap<String, Integer> vocabulary_one_way;
		ConcurrentHashMap<Integer, String> vocabulary_other_way;
		int[][] paragraphs;
		String prefix = "http://"+LANGUAGE+".wikipedia.org/wiki/";
		
		ContextBuilder(ConcurrentHashMap<String, Integer> vocabulary_one_way, 
				ConcurrentHashMap<Integer, String> vocabulary_other_way,
				int[][] paragraphs){
			this.vocabulary_one_way=vocabulary_one_way;
			this.vocabulary_other_way=vocabulary_other_way;
			this.paragraphs=paragraphs;
		}
		
		@Override
		public void doSomethingWithPage(String page) {
			// the page in question contains multiple lines
			String[] each_line=page.split("\n");
			int[] helper = new int[VocabularyBuilderWorker.getHashRank()+1];
			for (String line: each_line){
				
					String[] split = Util.fastSplit(line);
					if (split.length <= MIN_NB_CONTEXTS) {
						continue;
					}
					
					String resource = split[0];
					if (resource == null || resource.equals("") || resource.equals(" ")) {
						continue;
					}
					
					ArrayList<Integer> token_per_reference = new ArrayList<Integer>(30);
					for (int i = 1; i < split.length; i++) {
						Integer para_id = null;
						try {
							para_id = Integer.parseInt(split[i]);
						} catch (Exception e) {
						}
						;
						if (para_id != null && para_id != -1 && paragraphs[para_id] != null) {
							for (int indice_voc : paragraphs[para_id]) {
								helper[indice_voc]++;
								token_per_reference.add(indice_voc);
							}
						}
					}
					StringBuilder sb = new StringBuilder();
					sb.append(Util.uriStandardization(resource, prefix, LANGUAGE));
					sb.append("\t");
					sb.append('{');
					boolean first = true;
					
					ArrayList<TokenCount> tokenCounts=new ArrayList<ExtractContextualToken.ContextBuilder.TokenCount>();
					for (int i = 0; i < token_per_reference.size(); i++) {
						int indice_voc = token_per_reference.get(i);
						int count = helper[indice_voc];
						if (count != 0) {
							String actual_word = vocabulary_other_way.get(indice_voc);
							helper[indice_voc] = 0;
							if (count > 1) {
								TokenCount tk=new TokenCount();
								tk.token=actual_word;
								tk.count=count;
								tokenCounts.add(tk);

							}
						}
					}
					
					Collections.sort(tokenCounts, new CompTokenCount());
					for (TokenCount tk : tokenCounts){
						if (first)
							first = false;
						else
							sb.append(",");
						sb.append("(" + tk.token + "," + tk.count + ")");
					}
					sb.append("}");
					sb.append("\n");
					writeInOutput(sb.toString());
			}
		}
	}
	
	
	

	/**
	 * this function eventually outputs a count of token for each entry. It
	 * proceeds in two steps. The first part outputs two files the
	 * tmp_paragraph_file contains lines of the type
	 * "id_paragraph,,token1,,token2...". the id_paragraph is a unique integer
	 * between 0 and the total number of interesting paragraph) token come from
	 * the Lucene analyzer.
	 * 
	 * the reference_to_paragraphe file contains lines like
	 * "resource_name,,id_paragraphe_1,,id_paragraphe_2..."
	 * 
	 * A second step is then mae to use those two files to compute the final
	 * output that is wikipedia uri,{(token,count),(token,count)...}
	 * 
	 * @param path_to_dump
	 * @param analyzer_name
	 * @param path_to_stopwords
	 * @param path_to_output
	 */
	public static void extractContextualToken(String path_to_dump, String tmp_folder, String path_to_stopwords, String path_to_output,
			String path_to_uri_count, String path_to_redirections) {
		String path_to_tmp_paragraphs = tmp_folder+"tmp_paragraphes";
		String path_to_tmp_ref = tmp_folder+"tmp_referencess";
		/*************** FIRST STEP ************/

		// prepare the list of stopwords
		CharArraySet stopwords = new CharArraySet(0, false);
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path_to_stopwords)), "UTF8"), 16 * 1024);
			String line = br.readLine();
			while (line != null) {
				stopwords.add(line);
				line = br.readLine();
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		;

		// import the Set of existing Uri, and the redirection page. This will
		// be used to
		// redirect the uri we find in paragraphs
		Set<String> existing_Uri = new HashSet<String>();
		HashMap<String, String> redirections = new HashMap<String, String>();
		String prefix = "http://dbpedia.org/resource/";
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path_to_uri_count)), "UTF8"), 16 * 1024);
			String line = br.readLine();
			while (line != null) {
				String[] split = line.split("\t");
				String uri = split[0].substring(prefix.length());
				existing_Uri.add(uri);
				line = br.readLine();
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		;
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(path_to_redirections)), "UTF8"), 16 * 1024);
			String line = br.readLine();
			while (line != null) {
				String[] split = Util.fastSplit(line);
				redirections.put(split[0], split[1]);
				line = br.readLine();
			}
			br.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		;

		ConcurrentHashMap<String, List<Integer>> storage_references = new ConcurrentHashMap<String, List<Integer>>(5000000, 0.5f, 6);
		DumpParser dp = new DumpParser();
		dp.setAnOutput(path_to_tmp_paragraphs);
		for (int i = 0; i < 6; i++) {
			dp.addWorker(new ECTWorker(stopwords, ANAYZER_NAME, storage_references, existing_Uri, redirections));
		}

		// launch the extraction
		dp.extract(path_to_dump);

		long start = System.currentTimeMillis();
		/** output the concurrent hashmap to a file */
		try {
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(path_to_tmp_ref)), "UTF8"));
			ArrayList<String> all_resources = new ArrayList<String>();
			all_resources.addAll(storage_references.keySet());
			Collections.sort(all_resources);
			for (String resource : all_resources) {
				List<Integer> para_ids = storage_references.get(resource);
				if (para_ids.size() < MIN_NB_CONTEXTS)
					continue;
				StringBuilder sb = new StringBuilder();
				sb.append(resource);
				for (int i : para_ids) {
					sb.append(',');
					sb.append(',');
					sb.append(i);
				}
				sb.append('\n');
				bw.write(sb.toString());
			}
			bw.close();
		} catch (IOException ioe) {
		}
		storage_references = null;
		System.out.println("storage took " + (System.currentTimeMillis() - start));
		start = System.currentTimeMillis();
		System.gc();

		
		
		/*********** BEGINNING OF THE SECOND STEP ***********/
		// build the vocabulary, of course using a dump parser
		ConcurrentHashMap<String, Integer> vocabulary_one_way = new ConcurrentHashMap<String, Integer>(200000,0.5f,6);
		ConcurrentHashMap<Integer, String> vocabulary_other_way = new ConcurrentHashMap<Integer, String>(200000,0.5f,6);
		int[][] paragraphs = new int[ECTWorker.getNewHash()][];
		dp = new DumpParser();
		for (int i = 0; i < 6; i++) {
			dp.addWorker(new VocabularyBuilderWorker(vocabulary_one_way, vocabulary_other_way, paragraphs));
		}
		dp.setSplitByNumberOfLine(true);
		dp.extract(path_to_tmp_paragraphs);
		System.out.println("building voc " + (System.currentTimeMillis() - start));
		
		
		// and build the final file
		dp = new DumpParser();
		for (int i = 0; i < 6; i++) {
			dp.addWorker(new ContextBuilder(vocabulary_one_way, vocabulary_other_way, paragraphs));
		}
		dp.setAnOutput(path_to_output);
		dp.setSplitByNumberOfLine(true);
		dp.extract(path_to_tmp_ref);
		System.out.println("last step " + (System.currentTimeMillis() - start));

		

	}

	public static void main(String[] args) {

	}
}
