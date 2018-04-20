package me.dougclarknc.bakery;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SuppressWarnings("serial")
@WebServlet(name = "Reviewer", urlPatterns = "/reviewer")
public class ReviewerServlet extends HttpServlet {

	DatastoreService datastore;

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		PrintWriter out = resp.getWriter();
		Gson gson = new Gson();
		String keyString = req.getParameter("key");

		List<Entity> reviewers = new ArrayList<Entity>();
		Map<String, String> reviewerMap = new HashMap<String, String>();

		if (keyString.isEmpty()) {
			Query q = new Query("Reviewer").setFilter(new FilterPredicate("name", FilterOperator.NOT_EQUAL, ""));
			PreparedQuery pq = datastore.prepare(q);
			reviewers = pq.asList(FetchOptions.Builder.withDefaults());

		} else {
			Key key = KeyFactory.stringToKey(keyString);
			try {
				reviewers.add(datastore.get(key));
			} catch (EntityNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		for (Entity e : reviewers) {
			reviewerMap.put(KeyFactory.keyToString(e.getKey()), (String) e.getProperty("name"));
		}

		String jsonRes = gson.toJson(reviewerMap);
		out.println(jsonRes);
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		PrintWriter out = resp.getWriter();
		Gson gson = new Gson();

		// Create map of httpParameter that we want and run it through jSoup
		Type type = new TypeToken<Map<String, String>>() {
		}.getType();
		Map<String, String> reviewerContent = gson
				.fromJson(req.getReader().lines().collect(Collectors.joining(System.lineSeparator())), type);
		
		String keyString = reviewerContent.get("key");
		Entity reviewer = null;
		if (keyString == null) {
			reviewer = new Entity("Reviewer");
		} else {
			try {
				reviewer = datastore.get(KeyFactory.stringToKey(keyString));
			} catch (EntityNotFoundException enfe) {
				out.print(enfe.toString());
			}
		}
		

		reviewer.setProperty("name", reviewerContent.get("reviewerContent_name"));

		try {
			keyString = KeyFactory.keyToString(datastore.put(reviewer));

			// Send user to confirmation page with personalized confirm text
			String confirmation = "Reviewer with name " + reviewer.getProperty("name") + " and key " + keyString
					+ " created.";

			out.println(confirmation);
		} catch (DatastoreFailureException dsfe) {
			out.println("Failure: " + dsfe.toString());
			throw new ServletException("Datastore error", dsfe);
		}
	}

	@Override
	public void init() throws ServletException {
		datastore = DatastoreServiceFactory.getDatastoreService();
	}
}
