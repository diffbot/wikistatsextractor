package com.diffbot.wikistatsextractor.extractors;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Launcher {
	
	public static void main(String[] args){
		
		String conf_file="conf/extract_stats.config";
		if (args!=null && args.length>0){
			System.out.println("No argument provided, therefore, conf file is interpreted as conf/extract_stats.config");
			conf_file=args[0];
		}
		
		/** load the property file located in conf/extract_stats.config */
		Properties prop = new Properties();
		InputStream input = null;
		try {
			input = new FileInputStream(conf_file);
			prop.load(input);
		} catch (IOException ex) {
			ex.printStackTrace();
			return;
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		String tmp_folder=prop.getProperty("tmp_folder", "data/tmp/");
		String output_folder=prop.getProperty("output_folder", "data/output/");
		if (!tmp_folder.endsWith("/")) tmp_folder+="/";
		if (!output_folder.endsWith("/")) output_folder+="/";
		
		String language=prop.getProperty("language", "en");
		String dump_file=prop.getProperty("dump_file", "data/enwiki");
		String lucene_analyzer=prop.getProperty("lucene_analyzer", "en.EnglishAnalyzer");
		String path_to_stopwords=prop.getProperty("stop_words", "data/stopwords.en.list");
		
		long start=System.currentTimeMillis();
		
		/** set the parameters */
		ExtractSFAndRedirections.MAX_LENGTH_SF=Integer.parseInt(prop.getProperty("MAX_LENGTH_SF"));
		ExtractSFAndRedirections.MIN_LENGTH_SF=Integer.parseInt(prop.getProperty("MIN_LENGTH_SF"));;
		ExtractSFAndRedirections.MAX_NB_TOKEN_SF=Integer.parseInt(prop.getProperty("MAX_NB_TOKEN_SF"));;
		ExtractSFAndRedirections.MIN_OCCURENCE_COUPLE=Integer.parseInt(prop.getProperty("MIN_OCCURENCE_COUPLE"));;
		ExtractSFAndRedirections.LANGUAGE=language;
		
		/** extract all the surface forms, URI and redirections in the dump */
		ExtractSFAndRedirections.extractAllSurfaceFormsAndRedirection(dump_file, 
				output_folder+"pairCounts_"+language, 
				tmp_folder+"tmp_redirections_"+language, 
				output_folder+"uriCounts_"+language, 
				tmp_folder+"tmp_surface_form_counts_"+language);
		
		ExtractAllNGrams.LOCALE=prop.getProperty("LOCALE");
		ExtractAllNGrams.LANGUAGE=language;
		/** extract the ngrams: compute the number of time a surface form is a link compared to the number of time it 
		 *  is just a word  */
		ExtractAllNGrams.extractAllNGrams(dump_file,
				tmp_folder+"tmp_surface_form_counts_"+language, 
				output_folder+"sfAndTotalCounts_"+language);
		
		/** The longest (and most incomprehensible) step. 
		 *  For each resource, extract  the surrounding token */
		ExtractContextualToken.MAX_LENGTH_SF=Integer.parseInt(prop.getProperty("MAX_LENGTH_SF"));;
		ExtractContextualToken.MIN_LENGTH_SF=Integer.parseInt(prop.getProperty("MIN_LENGTH_SF"));
		ExtractContextualToken.MAX_NB_TOKEN_SF=Integer.parseInt(prop.getProperty("MAX_NB_TOKEN_SF"));
		ExtractContextualToken.LANGUAGE=language;
		ExtractContextualToken.ANAYZER_NAME=lucene_analyzer;
		ExtractContextualToken.MIN_NB_CONTEXTS=Integer.parseInt(prop.getProperty("MIN_NB_CONTEXTS"));
		
		ExtractContextualToken.extractContextualToken(dump_file,
				tmp_folder,
				path_to_stopwords,
				output_folder+"tokenCounts_"+language,
				output_folder+"uriCounts_"+language, 
				tmp_folder+"tmp_redirections_"+language);
		
		
		System.out.println("all done in "+((System.currentTimeMillis()-start)/1000)+" seconds");
		
	}

}
