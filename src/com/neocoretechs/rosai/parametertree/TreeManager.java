package com.neocoretechs.rosai.parametertree;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ros.namespace.GraphName;
import org.ros.namespace.NameResolver;
import org.ros.node.ConnectedNode;
import org.ros.node.parameter.ParameterTree;

/**
*<p>
* Example usage: get resolver for project "rocksack"
* NameResolver resolver = cache.getResolver("rocksack", "/projects/rocksack");
* resolve a namespaced key
* String resolved = cache.resolveParam("rocksack", "selectors/class/DatabaseManager", "/config/default/selectors/class_description");
*<p>
* invalidate when parameter changes
* cache.invalidate("rocksack");
*/
public class TreeManager {
	private static final Log log = LogFactory.getLog(TreeManager.class);
	private final ConcurrentHashMap<String, GraphName> graphNameCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, NameResolver> resolverCache = new ConcurrentHashMap<>();
	
	ConnectedNode connectedNode;
	ParameterTree parameterTree;
	private static volatile TreeManager instance = null;
	public static TreeManager getInstance() {
		synchronized(TreeManager.class) {
			if(instance == null) {
				instance = new TreeManager();
			}
			return instance;
		}
	}
	
	public void init(ConnectedNode connectedNode) {
		this.connectedNode = connectedNode;
		this.parameterTree = connectedNode.getParameterTree();
	}

	public void init(ParameterTree parameterTree) {
		this.parameterTree = parameterTree;
	}
	
	private TreeManager() {}
	/**
	 * Extract values from the RosJavaLite parameter tree.
	 * @param param the parameter tree
	 * @param key the key to extract
	 * @param defaultVal default if key not present
	 * @return the key
	 */
	public Object getOrDefault(String key, Object defaultVal) {
		Object v;
	    if (parameterTree.has(key)) {
	    	v = parameterTree.get(key, defaultVal);
	    } else {
	        parameterTree.set(key, defaultVal);
	        v = defaultVal;
	    }
	    return v; 
	}
	
	public void set(String key, Object val) {
		parameterTree.set(key, val);
	}
	/**
	 * Set up the parameter tree for parsing via ContentParser. The xPath array
	 * has title in (0,1) and xpath designator in (0,2)
	 * @see com.neocoretechs.rosai.contentprocessor.ContentParser
	 * @param xPath String array of xpath designator and its title
	 * @throws Exception
	 */
	public void setupParser(String[][] xPath) {
		JSONArray ja = new JSONArray();
		for(int i = 0; i < xPath.length; i++) {
			JSONObject desc = new JSONObject();
			desc.put("title",xPath[i][0]);
			desc.put("Xpath", xPath[i][1]);
			ja.put(i,desc);
		}
		getInstance().set("parse", ja.toString());
	}
	/**
	 * Use the graph name cache and resolver cache to extract a name resolver for the given namespace.<p>
	 * Extract the graph name of the namespace nsKey, if its not found use the default namespace defaultNs.
	 * Then place in the resolverCache a new name resolver of the result of that graph name extraction if
	 * we cant locate the nsKey in resolverCache. The new resolver will be a child of the main node resolver.
	 * @param nsKey The namespace key
	 * @param defaultNs default nmespace
	 * @return A resolver that can resolve names in this namespace
	 */
	public NameResolver getResolver(String nsKey, String defaultNs) {
	    String canonicalKey = canonicalize(nsKey);
	    GraphName g = graphNameCache.computeIfAbsent(canonicalKey, k -> {
	        String ns = (String) getOrDefault(canonicalKey, defaultNs);
	        try { return GraphName.of(ns); }
	        catch (IllegalArgumentException e) { return GraphName.of(defaultNs); }
	    });
	    return resolverCache.computeIfAbsent(canonicalKey, k -> connectedNode.getResolver().newChild(g));
	}
	
	/**
	 * Canonicalize project key
	 * @param raw
	 * @return
	 */
    private String canonicalize(String raw) {
        if (raw == null) return "default";
        return raw.trim().toLowerCase(Locale.ROOT);
    }
    
    /**
     * Invalidate caches for a project (call from param listener or admin)
     * @param projectKey
     */
    public void invalidate(String projectKey) {
        String key = canonicalize(projectKey);
        resolverCache.remove(key);
        graphNameCache.remove(key);
    }

    /**
     * Invalidate all (useful for config reload)
     */
    public void invalidateAll() {
        graphNameCache.clear();
        resolverCache.clear();
    }

    /**
     * Optional: list cached project keys
     * @return
     */
    public Set<String> listCachedKeys() {
        return Collections.unmodifiableSet(new HashSet<>(graphNameCache.keySet()));
    }

    /**
     * Helper: resolve a parameter key under a project's resolver
     * @param projectKey
     * @param relativeKey
     * @param fallback
     * @return
     */
    public String resolveParam(String projectKey, String relativeKey, String fallback) {
        NameResolver nr = getResolver(projectKey, "/");
        try {
            GraphName gn = GraphName.of(relativeKey);
            return nr.resolve(gn).toString();
        } catch (Exception e) {
            return fallback;
        }
    }

}
