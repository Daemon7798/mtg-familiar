/*
 * Copyright 2018 Adam Feinstein
 *
 * This file is part of MTG Familiar.
 *
 * MTG Familiar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MTG Familiar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MTG Familiar.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.gelakinetic.mtgfam.helpers.tcgp;

import com.gelakinetic.mtgfam.helpers.tcgp.JsonObjects.AccessToken;
import com.gelakinetic.mtgfam.helpers.tcgp.JsonObjects.CatalogData;
import com.gelakinetic.mtgfam.helpers.tcgp.JsonObjects.CategorySearchManifest;
import com.gelakinetic.mtgfam.helpers.tcgp.JsonObjects.GetProductInformationOptions;
import com.gelakinetic.mtgfam.helpers.tcgp.JsonObjects.ProductDetails;
import com.gelakinetic.mtgfam.helpers.tcgp.JsonObjects.ProductInformation;
import com.gelakinetic.mtgfam.helpers.tcgp.JsonObjects.ProductMarketPrice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;

public class TcgpApi {

    // Here's the full list of categories as of 1/28/18:
    // 1 Magic
    // 2 YuGiOh
    // 3 Pokemon
    // 4 Axis & Allies
    // 5 Boardgames
    // 6 D & D Miniatures
    // 7 Epic
    // 8 Heroclix
    // 9 Monsterpocalypse
    // 10 Redakai
    // 11 Star Wars Miniatures
    // 12 World of Warcraft Miniatures
    // 13 WoW
    // 14 Supplies
    // 15 Organizers & Stores
    // 16 Cardfight Vanguard
    // 17 Force of Will
    // 18 Dice Masters
    // 19 Future Card BuddyFight
    // 20 Weiss Schwarz
    // 21 My Little Pony
    // 22 TCGplayer
    // 23 Dragon Ball Z TCG
    // 24 Final Fantasy TCG
    // 25 Universal Fighting System
    // 26 Star Wars Destiny
    // 27 Dragon Ball Super CCG
    // 28 Dragoborne
    // 29 Funko
    // 30 MetaX TCG
    // 31 Card Sleeves
    // 32 Deck Boxes
    // 33 Card Storage Tins
    // 34 Life Counters
    // 35 Playmats
    // 36 Zombie World Order TCG
    // 37 The Caster Chronicles
    // 38 My Little Pony CCG
    // 39 Warhammer Books
    // 40 Warhammer Big Box Games
    // 41 Warhammer Box Sets
    // 42 Warhammer Clampacks
    // 43 Citadel Paints
    // 44 Citadel Tools
    // 45 Warhammer Game Accessories
    // 46 Books
    // 47 Exodus TCG
    // 48 Lightseekers TCG
    // 49 Protective Pages
    // 50 Storage Albums
    // 51 Collectible Storage
    // 52 Supply Bundles
    // 53 Munchkin CCG

    public static final int CATEGORY_ID_MAGIC = 1;

    private static final String TCGP_VERSION = "v1.7.1";
    private AccessToken mAccessToken;

    enum HttpMethod {
        GET,
        POST,
    }

    /**
     * TODO doc
     *
     * @param conn
     */
    private void setDefaultOptions(HttpURLConnection conn, HttpMethod method) throws ProtocolException {
        if (HttpMethod.GET == method) {
            conn.setDoOutput(false);
            conn.setRequestMethod("GET");
        } else if (HttpMethod.POST == method) {
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
        }
        conn.setInstanceFollowRedirects(false);
        conn.setUseCaches(false);
    }

    /**
     * Helper method to add the common header to an httpGet or httpPost
     *
     * @param conn The httpGet or httpPost to add the header to
     */
    private void addHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("Authorization", "bearer " + mAccessToken.access_token);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");
    }

    /**
     * This function requests an access token from TCGPlayer.com by providing the private keys. TODO A access token
     * should only be requested if we don't have a valid one stored. TODO When an access token is received, the token
     * and expiration date should be saved for later use.
     *
     * @param publicKey   Supplied by TCGPlayer.com, also referred to as the "client_id"
     * @param privateKey  Supplied by TCGPlayer.com, also referred to as the "client_secret"
     * @param accessToken Supplied by TCGPlayer.com, also referred to as the "X-Tcg-Access-Token"
     * @return An AccessToken object with the token, expiry date, etc, or null if something went wrong
     * @throws IOException If something goes wrong with the network
     */
    public AccessToken getAccessToken(String publicKey, String privateKey, String accessToken) throws IOException {

        // Only request an access token if we don't have one already
        if (null == mAccessToken) {
            // Create the connection with default options
            HttpURLConnection conn = (HttpURLConnection) new URL("http://api.tcgplayer.com/token").openConnection();
            setDefaultOptions(conn, HttpMethod.POST);

            // Set the header, special for the token request
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Tcg-Access-Token", accessToken);

            // Set the body and send the POST
            String payload = "grant_type=client_credentials&client_id=" + publicKey + "&client_secret=" + privateKey;
            conn.getOutputStream().write(payload.getBytes(Charset.forName("UTF-8")));

            // Get the response stream
            InputStream inStream = conn.getInputStream();

            // Parse the json out of the response and save it
            GsonBuilder builder = new GsonBuilder();
            AccessToken.setDateFormat(builder);
            this.mAccessToken = builder.create().fromJson(new InputStreamReader(inStream), AccessToken.class);

            // Clean up
            inStream.close();
            conn.disconnect();
        }

        // Return the saved access token
        return this.mAccessToken;
    }

    /**
     * Request and return the catalog data from TCGPlayer.com. This data contains all product categories, including
     * Magic, and product category metadata
     *
     * @return All of the catalog data or null if something went wrong
     * @throws IOException If something goes wrong with the network
     */
    public CatalogData getCatalogData() throws IOException {
        // Make sure we have an access token first
        if (null != mAccessToken) {

            // Parameters for the URL
            int offset = 0;
            int limit = 999;
            String sortOrder = "categoryId";
            boolean sortDesc = false;

            // Create the connection with default options and headers
            HttpURLConnection conn = (HttpURLConnection) new URL("http://api.tcgplayer.com/" + TCGP_VERSION + "/catalog/categories" + "?offset=" + offset + "&limit=" + limit + "&sortOrder=" + sortOrder + "&sortDesc=" + sortDesc).openConnection();
            setDefaultOptions(conn, HttpMethod.GET);
            addHeaders(conn);

            // Get the response stream. This opens the connection
            InputStream inStream = conn.getInputStream();

            // Parse the json out of the response and save it
            GsonBuilder builder = new GsonBuilder();
            CatalogData.CatalogDataItem.setDateFormat(builder);
            CatalogData catalogData = builder.create().fromJson(new InputStreamReader(inStream), CatalogData.class);

            // Clean up
            inStream.close();
            conn.disconnect();
            return catalogData;
        }
        // No access token
        return null;
    }

    /**
     * Request and return a search manifest describing all of the sorting options and filters that are available for the
     * given category
     *
     * @param categoryId The category to get a search manifest for
     * @return The search manifest
     * @throws IOException If something goes wrong with the network
     */
    public CategorySearchManifest getCategorySearchManifest(int categoryId) throws IOException {
        // Make sure we have an access token first
        if (null != mAccessToken) {

            // Create the connection with default options and headers
            HttpURLConnection conn = (HttpURLConnection) new URL("http://api.tcgplayer.com/" + TCGP_VERSION + "/catalog/categories/" + categoryId + "/search/manifest").openConnection();
            setDefaultOptions(conn, HttpMethod.GET);
            addHeaders(conn);

            // Get the response stream. This opens the connection
            InputStream inStream = conn.getInputStream();

            // Parse the json out of the response and save it
            Gson gson = new Gson();
            JsonParser parser = new JsonParser();
            JsonObject jo = parser.parse(new InputStreamReader(inStream)).getAsJsonObject();
            JsonArray results = jo.get("results").getAsJsonArray();

            CategorySearchManifest manifest = new CategorySearchManifest(jo.get("success").getAsBoolean(),
                    gson.fromJson(jo.get("errors"), String[].class),
                    gson.fromJson(results.get(0).getAsJsonObject().get("sorting"),
                            CategorySearchManifest.TextValuePair[].class),
                    gson.fromJson(results.get(0).getAsJsonObject().get("filters"),
                            CategorySearchManifest.FilterOptions[].class));

            // Clean up
            inStream.close();
            conn.disconnect();
            return manifest;
        }
        // No access token
        return null;
    }

    /**
     * Given a list of card names and expansions, request and return the product IDs for each
     *
     * @param name      The card name to get product information for
     * @param expansion The expansion of the card to get product information for
     * @throws IOException If something goes wrong with the network
     */
    public ProductInformation getProductInformation(String name, String expansion) throws IOException {
        // Make sure we have an access token first
        if (null != mAccessToken) {

            // Create the connection with default options and headers
            HttpURLConnection conn = (HttpURLConnection) new URL("http://api.tcgplayer.com/" + TCGP_VERSION + "/catalog/categories/" + CATEGORY_ID_MAGIC + "/search").openConnection();
            setDefaultOptions(conn, HttpMethod.POST);
            addHeaders(conn);

            // Add the information to search by
            GetProductInformationOptions options = new GetProductInformationOptions(
                    new GetProductInformationOptions.NameValuesPair[]{
                            new GetProductInformationOptions.NameValuesPair("ProductName", new String[]{name}),
                            new GetProductInformationOptions.NameValuesPair("SetName", new String[]{expansion})});
            conn.getOutputStream().write(new Gson().toJson(options, GetProductInformationOptions.class).getBytes(Charset.forName("UTF-8")));

            // Get the response stream. This opens the connection
            InputStream inStream = conn.getInputStream();

            // Parse the json out of the response and save it
            ProductInformation information = (new Gson()).fromJson(new InputStreamReader(inStream), ProductInformation.class);

            // Clean up
            inStream.close();
            conn.disconnect();
            return information;
        }
        // No access token
        return null;
    }

    /**
     * Given a productId, request and return that card's market price data
     *
     * @param productIds The productId of the card to query
     * @return All the market price information
     * @throws IOException If something goes wrong with the network
     */
    public ProductMarketPrice getProductMarketPrice(long[] productIds) throws IOException {
        // Make sure we have an access token first
        if (null != mAccessToken) {

            // Concatenate all the product IDs into one string
            StringBuilder stringIds = new StringBuilder();
            for (long id : productIds) {
                if (stringIds.length() == 0) {
                    stringIds = new StringBuilder(Long.toString(id));
                } else {
                    stringIds.append(',').append(Long.toString(id));
                }
            }

            // Create the connection with default options and headers
            HttpURLConnection conn = (HttpURLConnection) new URL("http://api.tcgplayer.com/" + TCGP_VERSION + "/pricing/product/" + stringIds.toString()).openConnection();
            setDefaultOptions(conn, HttpMethod.GET);
            addHeaders(conn);

            // Get the response stream. This opens the connection
            InputStream inStream = conn.getInputStream();

            // Parse the json out of the response and save it
            ProductMarketPrice price = new Gson().fromJson(new InputStreamReader(inStream), ProductMarketPrice.class);

            // Clean up
            inStream.close();
            conn.disconnect();
            return price;
        }
        // No access token
        return null;
    }

    /**
     * Given an array of productIds, request and return all of the product's non-price details
     *
     * @param productIds The productId of the card to query
     * @return All the non-price details
     * @throws IOException If something goes wrong with the network
     */
    public ProductDetails getProductDetails(long[] productIds) throws IOException {
        // Make sure we have an access token first
        if (null != mAccessToken) {

            // Concatenate all the product IDs into one string
            StringBuilder stringIds = new StringBuilder();
            for (long id : productIds) {
                if (stringIds.length() == 0) {
                    stringIds = new StringBuilder(Long.toString(id));
                } else {
                    stringIds.append(',').append(Long.toString(id));
                }
            }

            // Create the connection with default options and headers
            HttpURLConnection conn = (HttpURLConnection) new URL("http://api.tcgplayer.com/" + TCGP_VERSION + "/catalog/products/" + stringIds.toString()).openConnection();
            setDefaultOptions(conn, HttpMethod.GET);
            addHeaders(conn);

            // Get the response stream. This opens the connection
            InputStream inStream = conn.getInputStream();

            // Parse the json out of the response and save it
            GsonBuilder builder = new GsonBuilder();
            CatalogData.CatalogDataItem.setDateFormat(builder);
            ProductDetails details = builder.create().fromJson(new InputStreamReader(inStream), ProductDetails.class);

            // Clean up
            inStream.close();
            conn.disconnect();
            return details;
        }
        // No access token
        return null;
    }
}
