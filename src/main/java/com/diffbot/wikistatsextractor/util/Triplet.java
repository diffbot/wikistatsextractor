package com.diffbot.wikistatsextractor.util;
import java.util.Comparator;


/** I feel like this class may be useful.
 *  It is just a triplet (String uri, String surface_form, Integer count)
 *  @author sam
 *
 */
public class Triplet {
	public String uri, surface_form;
	public int count;
	public Triplet(String uri, String surface_form, int count) {
		this.uri=uri;
		if (uri==null)
			this.uri="-";
		this.surface_form=surface_form;
		if (surface_form==null)
			this.surface_form="-";
		this.count=count;
	}
	
	public static class SortByUri implements Comparator<Triplet>{
		public int compare(Triplet arg0, Triplet arg1) {
			if (arg0.uri==null)
				return 0;
			int comp1=arg0.uri.compareTo(arg1.uri);
			if (comp1!=0)
				return comp1;
			else
				return arg0.surface_form.compareTo(arg1.surface_form);
		}
	}
	
	public static class SortByCount implements Comparator<Triplet>{
		public int compare(Triplet arg0, Triplet arg1) {
			return -Integer.compare(arg0.count, arg1.count);
		}
	}
	
	public static boolean same_couple_uri_sf(Triplet t1, Triplet t2){
		if (t1.uri.equals(t2.uri) && t1.surface_form.equals(t2.surface_form))
			return true;
		return false;
	}
	
	public String toString(){
		return uri+",,"+surface_form+",,"+count;
	}

}
