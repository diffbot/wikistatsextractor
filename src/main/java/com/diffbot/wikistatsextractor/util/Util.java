 package com.diffbot.wikistatsextractor.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/** some Utils methods */
public class Util {

	/** xml patterns that will be unescaped. Have to be the longest first */
	private static String[] to_unescape = { "&amp;nbsp;", "&amp;mdash;", "&amp;ndash;", "&amp;", "&quot;", "&apos;", "&#039", "&mdash;", "&ndash", "&nbsp;" };
	private static char[] unescaped = {         '\u0020',      '\u2014',      '\u2013',    '&',      '"',     '\'',    '\'',  '\u2014', '\u2013', '\u0020' };

	/**
	 * One of the main methods. The older getCleanText was a bit more efficient,
	 * but the system of intput output was shit. Anyway, this thing takes a
	 * wikipedia page as input and output a list of String, one per paragraph.
	 * 
	 * By default, it will remove - the comments (&lt;!-- comment --&gt;) - the
	 * resources {| |}, and also the scripts {{ }}. This implies that we lose
	 * most of the tables, but I take the bet that enough text will remain. -
	 * the other markups (&lt; and &gt;)
	 * 
	 * Optionally you can remove: - the lists (ignore_lists), it will remove
	 * every line that starts with *,# or : - the references (&lt;ref [...]
	 * /ref&gt;) - and clean the links. if so, then
	 * "[[Something|a mystery thing]]" will become "a mystery thing", and
	 * "[[File:path/to/truc]]" will be removed.
	 * 
	 * filter_by_size boolean parameter says if the algorithm will throw
	 * out pages that are too small. This is false for abstracts since
	 * those pages are artificially shortened.
	 * 
	 * It also unescape all the remaining characters. If the page is not an
	 * article, then returns null
	 * */
	public static List<String> getCleanTextFromPage(String page, boolean ignore_lists, boolean ignore_ref, boolean clean_links, boolean filter_by_size) {
		/** if it is not a wikitext page, return null */
		if (!isWikiText(page)) {
			return null;
		}

		/** go to the index of the text */
		int index_text = page.indexOf("<text xml:space=\"preserve\">");
		if (index_text == -1)
			return null;
		index_text += "<text xml:space=\"preserve\">".length();

		/** locate the end */
		int end_text = page.indexOf("</text>");
		
		if(filter_by_size){
			/** text is too short */
			if (end_text - index_text < 100)
				return null;
		}

		StringBuilder sb = new StringBuilder();
		ArrayList<String> output = new ArrayList<String>();
		int nb_accolades = 0;
		int nb_semi_accolades = 0; // detects the "{|"
		boolean is_in_ref = false;
		boolean is_in_markup = false;
		boolean is_in_comment = false;
		boolean is_in_div = false;
		int div_level=0;
		boolean is_in_list = false;
		int nb_brackets = 0;
		int start_bracket = 0;
		int total_length = 0;

		for (int i = index_text - 1; i < end_text - 1; i++) {
			char c = page.charAt(i);
			char c_1 = page.charAt(i + 1);
			// for good measure, the text will virtually starts with a '\n' (the
			// list pattern includes '\n')
			if (i == index_text - 1) {
				c = '\n';
			}

			/** first we remove everything between {{ }} */
			if (c == '{' && c_1 == '{') {
				nb_accolades++;
				i++;
				continue;
			}
			if (c == '}' && c_1 == '}') {
				nb_accolades--;
				i++;
				continue;
			}
			if (nb_accolades > 0)
				continue;

			/** Then we remove everything between {| |} */
			if (c == '{' && c_1 == '|') {
				nb_semi_accolades++;
				i++;
				continue;
			}
			if (c == '|' && c_1 == '}') {
				nb_semi_accolades--;

				i++;
				continue;
			}
			if (nb_semi_accolades > 0)
				continue;

			/**
			 * if we specify it, we can ignore everything that is in a list
			 * (start with \n* or \n# or \n:).
			 */
			if (ignore_lists && c == '\n' && (c_1 == '*' || c_1 == '#' || c_1 == ':')) {
				i++;
				is_in_list = true;
				i--;
				continue;
			}
			if (ignore_lists && c == '\n' && is_in_list)
				is_in_list = false;
			if (ignore_lists && is_in_list)
				continue;

			/**
			 * if it is a new paragraph, so either \n\n or =\n or \n=, we put
			 * the content of the StringBuilder in a new String
			 */
			if (c == '\n' && (c_1 == '\n' || c_1 == '=' || page.charAt(i - 1) == '=')) {
				if (sb.length() > 1) {
					String text_paragraph=superTrim(sb.toString());
					if (text_paragraph.length()>1)
						output.add(text_paragraph);
					total_length += sb.length();
				}
				/** reset the stringbuilder */
				sb.setLength(0);
				;
			}

			/** deal with the comments (&lt;!-- --&gt;) */
			if (c == '&' && c_1 == 'l' && end_text > i + 7 && page.substring(i, i + 7).equals("&lt;!--")) {
				is_in_comment = true;
				i += 6;
				continue;
			}
			if (c == '-' && c_1 == '-' && end_text > i + 6 && page.substring(i, i + 6).equals("--&gt;")) {
				is_in_comment = false;
				i += 5;
				continue;
			}
			
			/** Sometimes there are some html div (yeah...)  in the dump, we try to remove then. */
			if (c == '&' && c_1 == 'l' && end_text > i + 7 && page.substring(i, i + 7).equals("&lt;div")) {
				is_in_div = true;
				div_level++;
				i += 6;
				continue;
			}
			if (c == '&' && c_1 == 'l' && end_text > i + 6 && page.substring(i, i + 12).equals("&lt;/div&gt;")) {
				div_level=Math.max(0, div_level-1);
				if (div_level==0)
					is_in_div = false;
				i += 11;
				continue;
			}

			/** remove the ''' ''' (bold) and === === (title) */
			if (c == '\'' && c_1 == '\'') {
				while (i < end_text && page.charAt(i) == '\'')
					i++;
				i--;
				continue;
			}
			if (c == '=' && c_1 == '=') {
				while (i < end_text && page.charAt(i) == '=')
					i++;
				i--;
				continue;
			}

			/** deals with the links */
			if (clean_links) {
				if (c == '[' && c_1 == '[') {
					nb_brackets++;
					if (nb_brackets == 1)
						start_bracket = i + 2;
					i++;
					continue;
				}

				if (c == ']' && c_1 == ']') {
					nb_brackets--;
					if (nb_brackets == 0 && !is_in_ref && !is_in_comment) {
						// time to look at what was in that link
						boolean inner_wiki_ref = false;
						int index_pipe = -1;
						for (int k = start_bracket; k < i; k++) {
							if (page.charAt(k) == ':') {
								inner_wiki_ref = true;
								break;
							}
							if (page.charAt(k) == '|')
								index_pipe = k + 1;
						}
						if (!inner_wiki_ref) {
							if (index_pipe != -1) {
								sb.append(Util.unescapeXML(page.substring(index_pipe, i)));
							} else {
								sb.append(Util.unescapeXML(page.substring(start_bracket, i)));
							}
						}
					}
					i++;
					continue;
				}
				if (nb_brackets > 0)
					continue;
			}

			if (ignore_ref) {
				/** deal with the references (&lt;ref&gt;) */
				if (c == '&' && c_1 == 'l' && end_text > i + 7 && page.substring(i, i + 7).equals("&lt;ref")) {
					is_in_ref = true;
					i += 6;
					/** particular case of the <ref name="thing"/> */
					int j = i;
					while (j < end_text - 5 && !page.substring(j, j + 4).equals("&gt;")) {
						j++;
					}
					if (page.charAt(j - 1) == '/') {
						is_in_ref = false;
						i = j + 3;
					}
					continue;
				}

				if (c == '&' && c_1 == 'l' && end_text > i + 12 && page.substring(i, i + 12).equals("&lt;/ref&gt;")) {
					is_in_ref = false;
					i += 11;
					continue;
				}
			}

			/**
			 * remove other kinds of markup. A markup starts with &lt;, and
			 * there is a &gt; less than 120 characters away
			 */
			if (c == '&' && !is_in_ref && !is_in_comment && end_text > i + 4 && c_1 == 'l' && page.charAt(i + 2) == 't' && page.charAt(i + 3) == ';') {
				// look if there is a &gt; less than 100 characters away. If
				// not, we don't remove it.
				int next_gt = page.indexOf("&gt;", i);
				if (next_gt != -1 && next_gt - 120 < i) {
					is_in_markup = true;
					i += 3;
					continue;
				}
			}
			if (!is_in_ref && c == '&' && end_text > i + 4 && c_1 == 'g' && page.charAt(i + 2) == 't' && page.charAt(i + 3) == ';') {
				is_in_markup = false;
				i += 3;
				continue;
			}

			/**
			 * And to finish, unescape remaining xml tags. See the to_unescape
			 * static variable
			 */
			if (c == '&') {
				boolean we_made_a_replacement = false;
				for (int unescaped_index = 0; unescaped_index < to_unescape.length; unescaped_index++) {
					String pattern = to_unescape[unescaped_index];
					boolean match = true;
					if (end_text <= i + pattern.length())
						continue;
					for (int i_sub = 0; i_sub < pattern.length(); i_sub++) {
						if (page.charAt(i + i_sub) != pattern.charAt(i_sub)) {
							match = false;
							break;
						}
					}
					if (match) {
						we_made_a_replacement = true;
						if (!is_in_ref && !is_in_markup && !is_in_comment && !is_in_list && !is_in_div)
							sb.append(unescaped[unescaped_index]);
						i += pattern.length() - 1;
						break;
					}
				}
				if (we_made_a_replacement)
					continue;
			}

			if (!is_in_ref && !is_in_markup && !is_in_comment && !is_in_list && !is_in_div) {
				sb.append(c);
			}
		}
		total_length += sb.length();
		
		if(filter_by_size){
			if (total_length < 100)
				return null;
		}

		String text_paragraph = superTrim(sb.toString());
		if (text_paragraph.length() > 1) {
			output.add(text_paragraph);
		}
		return output;

	}

	/** unescape a String in XML. . */
	public static String unescapeXML(String text) {
		if (text.contains("&")) {
			StringBuilder result = new StringBuilder(text.length());
			int n = text.length();
			for (int i = 0; i < n; i++) {
				char c = text.charAt(i);
				if (c == '&') {
					for (int unescaped_index = 0; unescaped_index < to_unescape.length; unescaped_index++) {
						String pattern = to_unescape[unescaped_index];
						boolean match = true;
						if (n <= i + pattern.length())
							continue;
						for (int i_sub = 0; i_sub < pattern.length(); i_sub++) {
							if (text.charAt(i + i_sub) != pattern.charAt(i_sub)) {
								match = false;
								break;
							}
						}
						if (match) {
							result.append(unescaped[unescaped_index]);
							i += pattern.length() - 1;
							break;
						}
					}
				} else {
					result.append(c);
				}
			}
			return result.toString().trim();
		} else
			return text.trim();
	}

	public static String truncateNumberSign(String s) {
		if (s.contains("#")) {
			int i = s.indexOf("#");
			return s.substring(0, i);
		} else {
			return s;
		}

	}

	/** transform "cabin luggage" in "Cabin luggage" */
	public static String upperifyFirstChar(String s) {
		if (s == null)
			return null;
		if (s.length() <= 1)
			return s.toUpperCase();
		return Character.toUpperCase(s.charAt(0)) + s.substring(1, s.length());
	}

	public static class PairUriSF {
		public String uri, surface_form;

		public PairUriSF(String uri, String surface_form) {
			this.uri = uri;
			this.surface_form = surface_form;

		}

		@Override
		public int hashCode() {
			return uri.hashCode() * 17 + surface_form.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof PairUriSF) {
				PairUriSF pusf = (PairUriSF) obj;
				if (uri.equals(pusf.uri) && surface_form.equals(pusf.surface_form))
					return true;
			}
			return false;
		}

		public String toString() {
			return uri + ",," + surface_form;
		}
	}

	/**
	 * return a list of pairs Uri, surface form found in the text Discards
	 * automatically those that start with a space, or a quote, or an & or are
	 * empty. The function accepts the restriction: max_length_sf : the maximum
	 * number of characters acceptable for a sf min_length_sf : the minimum
	 * max_terms_surface_form : the maximum number of token in the surface form
	 * (call the tokenizer, which at some point will depend on the language
	 * specified).
	 * */
	public static List<PairUriSF> getSurfaceFormsInString(String s, int max_length_sf, int min_length_sf, int max_token_sf, String language) {
		int len = s.length();
		ArrayList<PairUriSF> output = new ArrayList<Util.PairUriSF>();
		// the only difficulty is to deal with imbricated [[, like [[File: here
		// we [[Go]] ]], for instance
		int nb_brackets = 0;
		int start_bracket = 0;
		for (int i = 0; i < len - 1; i++) {
			if (s.charAt(i) == '[' && s.charAt(i + 1) == '[') {
				if (nb_brackets == 0)
					start_bracket = i + 2;
				nb_brackets++;
				i++;
			}
			if (s.charAt(i) == ']' && s.charAt(i + 1) == ']') {
				nb_brackets--;
				if (nb_brackets == 0) {
					// there it is the moment to look at what we have.
					String content = s.substring(start_bracket, i);
					if (content.contains(":") || content.startsWith("#"))
						continue;
					content = content.trim();
					if (content.endsWith("|"))
						continue;
					/** so two cases, now. Either there is a | or not */
					int index_pipe = content.indexOf('|');
					PairUriSF pusf;
					boolean same_sf = false;
					if (index_pipe != -1) {
						pusf = new PairUriSF(content.substring(0, index_pipe), content.substring(index_pipe + 1, content.length()));
					} else {
						pusf = new PairUriSF(content, content);
						same_sf = true;
					}

					/** remove the # if there is one in the uri */
					int index_diese = pusf.uri.indexOf('#');
					if (index_diese != -1) {
						pusf.uri = pusf.uri.substring(0, index_diese).trim();
						if (same_sf)
							pusf.surface_form = pusf.uri;
					}

					/** check if a surface form is acceptable */
					if (pusf.uri.length() == 0)
						continue;
					if (pusf.surface_form.length() < min_length_sf)
						continue;
					if (pusf.surface_form.length() > max_length_sf)
						continue;
					if (Tokenizer.getNbTokens(pusf.surface_form, language) > max_token_sf)
						continue;
					if (pusf.uri.charAt(0) == '&' || pusf.uri.charAt(0) == '\"' || pusf.uri.charAt(0) == '(' || pusf.uri.charAt(0) == '\''
							|| pusf.uri.charAt(0) == '-')
						continue;
					if (pusf.surface_form.charAt(0) == '&' || pusf.surface_form.charAt(0) == '\"' || pusf.surface_form.charAt(0) == '('
							|| pusf.surface_form.charAt(0) == '\'' || pusf.surface_form.charAt(0) == '-')
						continue;

					/**
					 * if we arrived here, this is an acceptable couple
					 * uri/surfaceform
					 */
					if (!pusf.surface_form.contains("\t")) {
						output.add(pusf);
					}

				}
			}

		}
		return output;
	}

	/**
	 * same thing as above, but also consider surface forms that are imbricated:
	 * [[File: here we [[Go]] ]] for instance, we return Go
	 * 
	 * @param s
	 * @param max_length_sf
	 * @param min_length_sf
	 * @param max_token_sf
	 * @param language
	 * @return
	 */
	public static List<PairUriSF> getAllSurfaceFormsInString(String s, int max_length_sf, int min_length_sf, int max_token_sf, String language) {
		int len = s.length();
		ArrayList<PairUriSF> output = new ArrayList<Util.PairUriSF>();
		Stack<Integer> starts = new Stack<>();

		// the only difficulty is to deal with imbricated [[, like [[File: here
		// we [[Go]] ]], for instance
		for (int i = 0; i < len - 1; i++) {
			if (s.charAt(i) == '[' && s.charAt(i + 1) == '[') {
				starts.add(i + 2);
				i++;
			}
			if (s.charAt(i) == ']' && s.charAt(i + 1) == ']') {
				if (starts.size() == 0) {
					// a stupid person closed some brackets.
					i++;
					continue;
				}
				int start_bracket = starts.pop();

				// there it is the moment to look at what we have.
				String content = s.substring(start_bracket, i);
				i++;
				if (content.contains(":") || content.startsWith("#"))
					continue;
				content = content.trim();
				if (content.endsWith("|"))
					continue;
				/** so two cases, now. Either there is a | or not */
				int index_pipe = content.indexOf('|');
				PairUriSF pusf;
				boolean same_sf = false;
				if (index_pipe != -1) {
					pusf = new PairUriSF(content.substring(0, index_pipe), content.substring(index_pipe + 1, content.length()));
				} else {
					pusf = new PairUriSF(content, content);
					same_sf = true;
				}

				/** remove the # if there is one in the uri */
				int index_diese = pusf.uri.indexOf('#');
				if (index_diese != -1) {
					pusf.uri = pusf.uri.substring(0, index_diese).trim();
					if (same_sf)
						pusf.surface_form = pusf.uri;
				}

				/** check if a surface form is acceptable */
				if (pusf.uri.length() == 0)
					continue;
				if (pusf.surface_form.length() < min_length_sf)
					continue;
				if (pusf.surface_form.length() > max_length_sf)
					continue;
				if (Tokenizer.getNbTokens(pusf.surface_form, language) > max_token_sf)
					continue;
				if (pusf.uri.charAt(0) == '&' || pusf.uri.charAt(0) == '\"' || pusf.uri.charAt(0) == '(' || pusf.uri.charAt(0) == '\''
						|| pusf.uri.charAt(0) == '-')
					continue;
				if (pusf.surface_form.charAt(0) == '&' || pusf.surface_form.charAt(0) == '\"' || pusf.surface_form.charAt(0) == '('
						|| pusf.surface_form.charAt(0) == '\'' || pusf.surface_form.charAt(0) == '-')
					continue;

				/**
				 * if we arrived here, this is an acceptable couple
				 * uri/surfaceform
				 */
				if (!pusf.surface_form.contains("\t")) {
					output.add(pusf);
				}

			}

		}
		return output;
	}

	public static String cleanSurfaceForms(String s) {
		int len = s.length();
		StringBuilder output = new StringBuilder();
		// the only difficulty is to deal with imbricated [[, like [[File: here
		// we [[Go]] ]], for instance
		int nb_brackets = 0;
		int start_bracket = 0;
		for (int i = 0; i < len - 1; i++) {
			char c = s.charAt(i), c_1 = s.charAt(i + 1);
			if (c == '[' && c_1 == '[') {
				if (nb_brackets == 0)
					start_bracket = i + 2;
				nb_brackets++;
				i++;
			}
			if (c == ']' && c_1 == ']') {
				nb_brackets--;
				if (nb_brackets == 0) {
					// time to look at what was in that link
					boolean inner_wiki_ref = false;
					int index_pipe = -1;
					for (int k = start_bracket; k < i; k++) {
						if (s.charAt(k) == ':') {
							inner_wiki_ref = true;
							break;
						}
						if (s.charAt(k) == '|')
							index_pipe = k + 1;
					}
					if (!inner_wiki_ref) {
						if (index_pipe != -1) {
							output.append(Util.unescapeXML(s.substring(index_pipe, i)));
						} else {
							output.append(Util.unescapeXML(s.substring(start_bracket, i)));
						}
						i += 1;
						continue;
					}
				}
			}
			if (nb_brackets == 0) {
				output.append(c);
			}
		}
		return output.toString();
	}

	/** get the title of the page. */
	public static String getTitle(String page) {
		int start_title = page.indexOf("<title>");
		int end_title = page.indexOf("</title>", start_title);
		String title = page.substring(start_title + 7, end_title);
		title = title.trim();
		title = unescapeXML(title);
		return title;
	}

	/** return true if the mode of the page is "wikitext" */
	public static boolean isWikiText(String page) {
		int index_model = page.indexOf("<model>");
		/** check if this page is the page of an article. */
		if (index_model == -1 || !page.substring(index_model, index_model + 30).startsWith("<model>wikitext"))
			return false;
		return true;
	}

	/** escape à la wikipédia */
	public static String escapeWiki(String s) {
		s = s.replaceAll(" ", "_");
		s = s.replaceAll("\\t", "_");
		try {
			s = URLEncoder.encode(s, "UTF8");
			return s;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return "";
	}

	
	public final static char[] uri_to_replace=   {   '"',   '#',   '%',   '<',   '>',   '?',   '[',   '\\',   ']',   '^',   '`',   '{',   '|',   '}',   '\''};
	public final static String[] uri_replacement={ "%22", "%23", "%25", "%3C", "%3E", "%3F", "%5B",  "%5C", "%5D", "%5E", "%60", "%7B", "%7C", "%7D", "%27"};
	/** Standardization method for any URI, compatible with dbpedia extraction framework
	 *  - All dbpedia/wikipedia entity Uris start with http://dbpedia.org/resource/
	 *  - We then apply UrlDecoder.decode() until no '%' char remains
	 *  - White space are replaced by '_', same for '\t'
	 *  - The first letter for the suffix is uppercased.
	 *  - Several spaces are transformed into one
	 *  - the suffix is trimmed
     *  - The following characters are percent encoded "#%<>?[\]^`{|}
     */
	public static String uriStandardization(String uri, String optional_prefix, String optional_language){
		String suffix=uri;
		String standard_prefix="http://dbpedia.org/resource/";
		if (optional_language!=null && !optional_language.equals("en"))
			standard_prefix="http://"+optional_language+".dbpedia.org/resource/";
		if (suffix.contains("/") && !suffix.endsWith("/")){
			int index_last_slash=suffix.lastIndexOf('/');
			suffix=uri.substring(index_last_slash+1);
		}
		
		// apply UrlDecoder until there is no % remaining
		int counter=0;
		while(suffix.contains("%") && counter<5){
			try{suffix=URLDecoder.decode(suffix, "UTF-8");
			}catch(Exception e){};
			counter++;
		}
		
		// replace ' ' with '\t'
		suffix = suffix.trim();
		suffix = suffix.replaceAll(" +", "_");
		suffix = suffix.replaceAll("\\t", "_");
		if (suffix.length()==0)
			suffix= "incorrect_uri";
		if (!Character.isUpperCase(suffix.charAt(0)))
			suffix=Character.toUpperCase(suffix.charAt(0))+suffix.substring(1);
		
		// escape the following characters "#%<>?[\]^`{|}
		StringBuilder sb=new StringBuilder();
		for (int i=0; i<suffix.length(); i++){
			char c=suffix.charAt(i);
			boolean replaced=false;
			for (int j=0; j<uri_to_replace.length; j++){
				if (c==uri_to_replace[j]){
					sb.append(uri_replacement[j]);
					replaced=true;
					break;
				}
			}
			if (!replaced)
				sb.append(c);
		}
		suffix=sb.toString();
		
		if (optional_prefix==null)
			return standard_prefix+suffix;
		return optional_prefix+suffix;
	}
	
	/** split s with ",," quite fast */
	public static String[] fastSplit(String s) {
		if (s.length() <= 2) {
			return new String[] { s };
		}

		int len = s.length();
		int nb_token = 0;
		for (int i = 0; i < len - 1; i++) {
			if (s.charAt(i) == ',' && s.charAt(i + 1) == ',') {
				nb_token++;
				i++;
			}
		}
		if (s.charAt(len - 1) != ',' || s.charAt(len - 2) != ',') {
			nb_token++;
		}
		String[] output = new String[nb_token];
		int last = 0;
		nb_token = 0;
		for (int i = 0; i < len - 1; i++) {
			if (s.charAt(i) == ',' && s.charAt(i + 1) == ',') {
				output[nb_token] = s.substring(last, i);
				nb_token++;
				i++;
				last = i + 1;
			}
		}
		if (s.charAt(len - 1) != ',' || s.charAt(len - 2) != ',') {
			output[nb_token] = s.substring(last, len);
			nb_token++;
		}
		return output;

	}

	public static long longHashcode(String s) {
		long h = 1125899906842597L; // prime
		int len = s.length();

		for (int i = 0; i < len; i++) {
			h = 31 * h + s.charAt(i);
		}
		return h;
	}

	private static boolean[] is_white_map = null;

	/**
	 * The java trim() function, but faster.
	 * 
	 * @return
	 */
	private static final String superTrim(String s) {
		if (s==null)
			return "";
		if (s.length()==0)
			return "";
		/** initialize the lookup */
		if (is_white_map == null) {
			synchronized (Util.class) {
				if (is_white_map == null) {
					boolean[] arr = new boolean[256 * 256];
					/** Unicode separators. See javadoc of Character.isWhitespace(c)*/
					char[] unicode_whitespace = { '\u0020', '\u1680', '\u2000', '\u2001', '\u2002', 
							'\u2003', '\u2004', '\u2005', '\u2006', '\u2008', '\u2009',
							'\u200A', '\u205F', '\u3000', '\u2028', '\u2029', '\t', '\n', '\f', '\r', 
							'\u000B', '\u001C', '\u001D', '\u001E', '\u001F' };
					for (char c2 : unicode_whitespace)
						arr[c2] = true;
					is_white_map = arr;
				}
			}
		}
		
		/** trim properly talking */
		int real_start=-1;
		int len=s.length();
		for (int i=0; i<len; i++){
			if (!is_white_map[s.charAt(i)]){
				real_start=i;
				break;
			}
		}
		if (real_start==-1)
			return "";
		int real_end=-1;
		for (int i=len-1; i>=0; i--){
			if (!is_white_map[s.charAt(i)]){
				real_end=i+1;
				break;
			}
		}
		// Should never be true
		if (real_start==-1)
			return "";
		return s.substring(real_start, real_end);
	}

	public static void main(String[] args) {
		System.out.println(uriStandardization("http://dbpedia.org/resource/ chouette#partyé  (blabal)", null, null));
	}

	public static Integer getNumberProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}


	/**
	 * Gets correct uri of page, following any redirects.
	 * 
	 * @param page
	 * @return
	 */
	public static String getResolvedPageUri(String page, String language) {
		/** look for the title of the page */
		String title = Util.getTitle(page);

		if (page.contains("<redirect title=")) {
			int start_redirect = page.indexOf("<redirect title=");
			int end_redirect = page.indexOf("/>", start_redirect);
			String redirect = page.substring(start_redirect + 16, end_redirect);
			redirect = redirect.trim();
			redirect = redirect.replaceAll("\"", "");
			redirect = Util.unescapeXML(redirect);
			return uriStandardization(redirect, null, language);

		} else {
			return uriStandardization(title, null, language);
		}
	}

	/**
	 * Returns true if the page redirects to a different page
	 * 
	 * @param page
	 * @return
	 */
	public static boolean pageRedirects(String page) {
		if (page.contains("<redirect title=")) {

			return true;
		}
		return false;
	}
}
