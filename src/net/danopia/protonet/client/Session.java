/**
 *
 */
package net.danopia.protonet.client;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.database.Cursor;

public class Session {
	public Fetcher client = null;
	public String mainPage = null;
	public ArrayList<String[]> courses = null;
	//public HashMap<String, Course> coursePages = null;

	public Session(Cursor mCursor) {
		client = new Fetcher(mCursor.getString(1));

		//coursePages = new HashMap<String, Course>();

		try {
			String page = client.doLogin(mCursor.getString(2), mCursor.getString(3));
			parseHome(page);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void parseHome(String content) {
		mainPage = content;

		courses = new ArrayList<String[]>();
		Pattern pattern = Pattern.compile("<td>([0-9]+).+?</td>.+?<td align=\"left\">(.+?)<br>.+?<a href=\"mailto:(.+?)\">(.+?)</a>.+?href=\"(.+?)\">([0-9\\-]+)<", Pattern.DOTALL);
	    Matcher matcher = pattern.matcher(content);

	    while (matcher.find()) {
	    	String period = matcher.group(1).toString();
	    	String course = matcher.group(2).toString();
	    	String email = matcher.group(3).toString();
	    	String teacher = matcher.group(4).toString();
	    	String url = matcher.group(5).toString();
	    	String avg = matcher.group(6).toString();

	    	String[] info = new String[] {period, course, email, teacher, url, avg};
	    	courses.add(info);
	    }
	}

	/*public Course getCourse(String[] course) {
		return getCourse(course[4]);
	}

	public Course getCourse(String url) {
		if (coursePages.containsKey(url)) {
    		return coursePages.get(url);
    	} else {
        	Course page = new Course(this, url);
        	coursePages.put(url, page);
        	return page;
    	}
	}*/
}
