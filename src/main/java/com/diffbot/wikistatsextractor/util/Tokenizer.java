package com.diffbot.wikistatsextractor.util;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Locale;



/** by default, a simple space tokenizer. It should definitely be completed by Something better for chinese */
public class Tokenizer {
	
	/** quickly get the nb of token in that String */
	public static int getNbTokens(String s, String language){
		int len=s.length();
		int nb_tokens=0;
		for (int i=1; i<len; i++){
			if (s.charAt(i)==' ' && s.charAt(i-1)!=' ')
				nb_tokens++;
		}
		return nb_tokens;
	}
	
	/** get the index of the begining of each word. I prefer to do in two passes and output an array, 
	 *  this way dealing with the memory may be faster. */
	public static int[] getDelimiters(String s){
		int len=s.length();
		int nb_token=0;
		boolean previous_is_delimiter=true;
		for (int i=0; i<len; i++){
			char c=s.charAt(i);
			if (tokenize_isdelim(c)){
				if (!previous_is_delimiter){
					nb_token++;
				}
				previous_is_delimiter=true;
			}
			else{
				if (previous_is_delimiter){
					nb_token++;
				}
				previous_is_delimiter=false;
			}
		}
		int[] delimiters=new int[nb_token];
		int counter=0;
		previous_is_delimiter=true;
		for (int i=0; i<len; i++){
			char c=s.charAt(i);
			if (tokenize_isdelim(c)){
				if (!previous_is_delimiter){
					delimiters[counter]=i;
					counter++;
				}
				previous_is_delimiter=true;
			}
			else{
				if (previous_is_delimiter){
					delimiters[counter]=i;
					counter++;
				}
				previous_is_delimiter=false;
			}
		}
		return delimiters;
		
	}
	
	/** a lookup for delimiters. Absolutely fucking fast */
	private static boolean[] is_delim_map=null;									
	private static final char[] puncts = "!\"#%&'()*+,-./:;<=>?@[\\]^_`{|}~\u2019\u201c\u201d\u2018\u2026\u2014".toCharArray();
	private static final boolean tokenize_isdelim(char c)
	{
		if (is_delim_map!=null)
			return is_delim_map[(int)c];
		else{
			synchronized(Tokenizer.class){
				if (is_delim_map!=null)
					return is_delim_map[(int)c];
				boolean[] arr=new boolean[256*256];
				/** unicode separators. See javadoc of Character.isWhitespace(c) */
				char[] unicode_whitespace={'\u0020','\u1680','\u2000',
						'\u2001','\u2002','\u2003','\u2004','\u2005',
						'\u2006','\u2008','\u2009','\u200A',
						'\u205F','\u3000','\u2028','\u2029',
						'\t','\n','\f','\r','\u000B',
						'\u001C','\u001D','\u001E','\u001F'};
				for (char c2 : unicode_whitespace)
					arr[(int)c2]=true;
				/** We add the punctuation signs */
				for (char c2: puncts)
					arr[(int)c2]=true;
				is_delim_map=arr;
				return is_delim_map[(int)c];
			}
		}
	}
	
	/** Uses the BreakIndexer class from java */
	public static ArrayList<Integer> getDelimiters2(String s, String language){
		ArrayList<Integer> list_boundaries=new ArrayList<Integer>();
		BreakIterator bi=BreakIterator.getWordInstance(new Locale(language));
		
		
		bi.setText(s);
		int start=bi.first();
		
		int end = 0;
		try {
		      end=bi.next();
		} catch(ArrayIndexOutOfBoundsException e) {
		       bi.setText( java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "") );
		       end=bi.next();
		}
		
		while (end != BreakIterator.DONE) {
			if (end==start+1){
				char c=s.charAt(start);
				if (!tokenize_isdelim(c)){
					list_boundaries.add(start);
					list_boundaries.add(end);
				}
					
			}
			else{
					list_boundaries.add(start);
					list_boundaries.add(end);
			}

	      start = end;
	      end = bi.next();
		}
		return list_boundaries;
		
	}
	
	public static void main(String[] args){
	}
	
	
	
}
