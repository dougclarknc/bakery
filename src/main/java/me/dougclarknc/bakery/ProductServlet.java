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
@WebServlet(name = "Product", urlPatterns = { "/product" })
public class ProductServlet extends HttpServlet {

	DatastoreService datastore;
	
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		Gson gson = new Gson();
		PrintWriter out = response.getWriter();
		
		// Create map of httpParameter that we want and run it through jSoup
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		Map<String,String> productContent = gson.fromJson(request.getReader().lines().collect(Collectors.joining(System.lineSeparator())), type);

		String keyString = productContent.get("key");
		Entity product = null;
		if (keyString == null) {
			product = new Entity("Product");
		} else {
			try {
				product = datastore.get(KeyFactory.stringToKey(keyString));
			} catch (EntityNotFoundException enfe) {
				out.print(enfe.toString());
			}
		}

		product.setProperty("title", productContent.get("productContent_title"));
		product.setProperty("versions", productContent.get("productContent_versions"));

		try {
			keyString = KeyFactory.keyToString(datastore.put(product));

			// Send user to confirmation page with personalized confirm text
			String confirmation = "Product with title " + product.getProperty("title") + "and versions " + product.getProperty("versions") +" and key " + keyString + " created.";

			out.println(confirmation);
		} catch (DatastoreFailureException dsfe) {
			out.println("Failure: " + dsfe.toString());
			throw new ServletException("Datastore error", dsfe);
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		PrintWriter out = resp.getWriter();
		Gson gson = new Gson();
		String keyString = req.getParameter("key");

		// Create map of httpParameter that we want and run it through jSoup
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		Map<String,String> productContent = gson.fromJson(req.getReader().lines().collect(Collectors.joining(System.lineSeparator())), type);

		List<Entity> products = new ArrayList<Entity>();
		Map<String, String[]> productMap = new HashMap<String, String[]>();

		if (productContent.isEmpty()) {
			Query q  = new Query("Product").setFilter(new FilterPredicate("title", FilterOperator.NOT_EQUAL, ""));
			PreparedQuery pq = datastore.prepare(q);
			products = pq.asList(FetchOptions.Builder.withDefaults());
		} else {
			Key key = KeyFactory.stringToKey(keyString);
			try {
				products.add(datastore.get(key));
			} catch (EntityNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		for (Entity e : products) {
			String[] data = {(String) e.getProperty("name"), (String) e.getProperty("versions")};
			productMap.put(KeyFactory.keyToString(e.getKey()), data);
		}
		
		String jsonRes = gson.toJson(productMap);
		out.println(jsonRes);
	}

	@Override
	public void init() throws ServletException {
		datastore = DatastoreServiceFactory.getDatastoreService();
	}

}