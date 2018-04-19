package me.dougclarknc.bakery;

import java.io.IOException;
import java.io.PrintWriter;
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
import com.google.gson.Gson;

@SuppressWarnings("serial")
@WebServlet(name = "Recipe", urlPatterns = { "/recipe" })
public class RecipeServlet extends HttpServlet {

	DatastoreService datastore;

	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		PrintWriter out = response.getWriter();
		Gson gson = new Gson();

		// Create map of httpParameter that we want and run it through jSoup
		Map<String, String> recipeContent = request.getParameterMap().entrySet().stream()
				.filter(a -> a.getKey().startsWith("recipeContent_"))
				.collect(Collectors.toMap(p -> p.getKey(), p -> Jsoup.clean(p.getValue()[0], Whitelist.basic())));
		Entity recipe = null;
		Key key = KeyFactory.stringToKey(recipeContent.get("recipeContent_key"));
		try {
			recipe = datastore.get(key);
		} catch (EntityNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		String jsonRes = gson.toJson(recipe);
		out.println(jsonRes);
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// Create map of httpParameter that we want and run it through jSoup
		Map<String, String> recipeContent = request.getParameterMap().entrySet().stream()
				.filter(a -> a.getKey().startsWith("recipeContent_"))
				.collect(Collectors.toMap(p -> p.getKey(), p -> Jsoup.clean(p.getValue()[0], Whitelist.basic())));
		Entity recipe = new Entity("Recipe");

		recipe.setProperty("producttitle", recipeContent.get("recipeContent_producttitle"));
		recipe.setProperty("version", recipeContent.get("recipeContent_version"));
		recipe.setProperty("text", "recipeContent_text");

		try {
			datastore.put(recipe);

			// Send user to confirmation page with personalized confirm text
			String confirmation = "recipe with key " + recipe.getKey().toString() + " created.";

			request.setAttribute("confirmation", confirmation);
			request.getRequestDispatcher("/confirm.jsp").forward(request, response);
		} catch (DatastoreFailureException dsfe) {
			throw new ServletException("Datastore error", dsfe);
		}
	}

	@Override
	public void init() throws ServletException {
		datastore = DatastoreServiceFactory.getDatastoreService();
	}

}
