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
@WebServlet(name = "Recipe", urlPatterns = { "/recipe" })
public class RecipeServlet extends HttpServlet {

	DatastoreService datastore;

	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		Gson gson = new Gson();
		String keyString = request.getParameter("key");


		// Create map of httpParameter that we want and run it through jSoup
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		Map<String,String> recipeContent = gson.fromJson(request.getReader().lines().collect(Collectors.joining(System.lineSeparator())), type);
				
		List<Entity> recipes = new ArrayList<Entity>();
		Map<String, String[]> recipeMap = new HashMap<String, String[]>();
		
		if (recipeContent.isEmpty()) {
			Query q  = new Query("Recipe").setFilter(new FilterPredicate("version", FilterOperator.NOT_EQUAL, ""));
			PreparedQuery pq = datastore.prepare(q);
			recipes = pq.asList(FetchOptions.Builder.withDefaults());
		} else {
			Key key = KeyFactory.stringToKey(keyString);
			try {
				recipes.add(datastore.get(key));
			} catch (EntityNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		for (Entity e : recipes) {
			String[] data = { (String)e.getProperty("productTitle"), (String)e.getProperty("version"), (String)e.getProperty("text") };
			recipeMap.put(KeyFactory.keyToString(e.getKey()), data);
		}

		String jsonRes = gson.toJson(recipeMap);
		out.println(jsonRes);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		Gson gson = new Gson();
		PrintWriter out = response.getWriter();
		
		// Create map of httpParameter that we want and run it through jSoup
		Type type = new TypeToken<Map<String, String>>(){}.getType();
		Map<String,String> recipeContent = gson.fromJson(request.getReader().lines().collect(Collectors.joining(System.lineSeparator())), type);
		
		String keyString = recipeContent.get("key");
		Entity recipe = null;
		if (keyString == null) {
			recipe = new Entity("Recipe");
		} else {
			try {
				recipe = datastore.get(KeyFactory.stringToKey(keyString));
			} catch (EntityNotFoundException enfe) {
				out.print(enfe.toString());
			}
		}

		recipe.setProperty("productTitle", recipeContent.get("recipeContent_productTitle"));
		recipe.setProperty("version", recipeContent.get("recipeContent_version"));
		recipe.setProperty("text", "recipeContent_text");

		try {
			datastore.put(recipe);

			// Send user to confirmation page with personalized confirm text
			String confirmation = "Recipe with product title " + recipe.getProperty("productTitle") + "and version " + recipe.getProperty("version") + " and key " + recipe.getKey().toString() + " created.";

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
