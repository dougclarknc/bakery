package me.dougclarknc.bakery;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;

import com.google.appengine.api.datastore.DatastoreFailureException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.gson.Gson;

/**
 * Servlet implementation class Reviewer
 */
@SuppressWarnings("serial")
@WebServlet(name = "Reviewer", urlPatterns = "/reviewer")
public class Reviewer extends HttpServlet {

	DatastoreService datastore;

	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		PrintWriter out = resp.getWriter();
		Gson gson = new Gson();

		// Create map of httpParameter that we want and run it through jSoup
		Map<String, String> reviewerContent = req.getParameterMap().entrySet().stream()
				.filter(a -> a.getKey().startsWith("reviewerContent_"))
				.collect(Collectors.toMap(p -> p.getKey(), p -> Jsoup.clean(p.getValue()[0], Whitelist.basic())));

		List<Entity> reviewers = new ArrayList<Entity>();
		if (reviewerContent.isEmpty()) {
			Query q = new Query("Reviewer").setFilter(new FilterPredicate("name", FilterOperator.NOT_EQUAL, ""));
			PreparedQuery pq = datastore.prepare(q);
			reviewers = pq.asList(null);
		} else {
			Key key = KeyFactory.stringToKey(reviewerContent.get("reviewerContent_key"));
			try {
				reviewers.add(datastore.get(key));
			} catch (EntityNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		String jsonRes = gson.toJson(reviewers);
		out.println(jsonRes);
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// Create map of httpParameter that we want and run it through jSoup
		Map<String, String> reviewerContent = req.getParameterMap().entrySet().stream()
				.filter(a -> a.getKey().startsWith("reviewerContent_"))
				.collect(Collectors.toMap(p -> p.getKey(), p -> Jsoup.clean(p.getValue()[0], Whitelist.basic())));

		Query q = new Query("Reviewer").setFilter(
				new FilterPredicate("title", FilterOperator.EQUAL, reviewerContent.get("reviewerContent_name")));
		PreparedQuery pq = datastore.prepare(q);

		Entity reviewer = pq.asSingleEntity();
		if (reviewer == null)
			reviewer = new Entity("Reviewer");

		reviewer.setProperty("title", reviewerContent.get("reviewerContent_name"));

		try {
			datastore.put(reviewer);

			// Send user to confirmation page with personalized confirm text
			String confirmation = "Reviewer with name " + reviewerContent.get("reviewerContent_name") + " created.";

			req.setAttribute("confirmation", confirmation);
			req.getRequestDispatcher("/confirm.jsp").forward(req, resp);
		} catch (DatastoreFailureException dsfe) {
			throw new ServletException("Datastore error", dsfe);
		}
	}

	@Override
	public void init() throws ServletException {
		datastore = DatastoreServiceFactory.getDatastoreService();
	}
}
