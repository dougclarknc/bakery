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

@SuppressWarnings("serial")
@WebServlet(name = "Product", urlPatterns = { "/product" })
public class ProductServlet extends HttpServlet {

	DatastoreService datastore;
	
	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		// Create map of httpParameter that we want and run it through jSoup
		Map<String, String> productContent = request.getParameterMap().entrySet().stream()
				.filter(a -> a.getKey().startsWith("productContent_"))
				.collect(Collectors.toMap(p -> p.getKey(), p -> Jsoup.clean(p.getValue()[0], Whitelist.basic())));

		Query q = new Query("Product").setFilter(
				new FilterPredicate("title", FilterOperator.EQUAL, productContent.get("productContent_title")));
		PreparedQuery pq = datastore.prepare(q);

		Entity product = pq.asSingleEntity();
		if (product == null)
			product = new Entity("Product");

		product.setProperty("title", productContent.get("productContent_title"));
		product.setProperty("versions", productContent.get("productContent_versions"));

		try {
			datastore.put(product);

			// Send user to confirmation page with personalized confirm text
			String confirmation = "Product with title " + productContent.get("productContent_title") + " created.";

			request.setAttribute("confirmation", confirmation);
			request.getRequestDispatcher("/confirm.jsp").forward(request, response);
		} catch (DatastoreFailureException dsfe) {
			throw new ServletException("Datastore error", dsfe);
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		PrintWriter out = resp.getWriter();
		Gson gson = new Gson();
		
		// Create map of httpParameter that we want and run it through jSoup
		Map<String, String> productContent = req.getParameterMap().entrySet().stream()
				.filter(a -> a.getKey().startsWith("productContent_"))
				.collect(Collectors.toMap(p -> p.getKey(), p -> Jsoup.clean(p.getValue()[0], Whitelist.basic())));

		List<Entity> products = new ArrayList<Entity>();
		if (productContent.isEmpty()) {
			Query q  = new Query("Product").setFilter(new FilterPredicate("title", FilterOperator.NOT_EQUAL, ""));
			PreparedQuery pq = datastore.prepare(q);
			products = pq.asList(null);
		} else {
			Key key = KeyFactory.stringToKey(productContent.get("productContent_key"));
			try {
				products.add(datastore.get(key));
			} catch (EntityNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		String jsonRes = gson.toJson(products);
		out.println(jsonRes);
	}

	@Override
	public void init() throws ServletException {
		datastore = DatastoreServiceFactory.getDatastoreService();
	}

}