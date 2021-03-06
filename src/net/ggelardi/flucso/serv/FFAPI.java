package net.ggelardi.flucso.serv;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.ggelardi.flucso.serv.Commons.PK;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Request;
import retrofit.client.UrlConnectionClient;
import retrofit.http.Body;
import retrofit.http.EncodedPath;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit.mime.MultipartTypedOutput;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Base64;

import com.google.gson.annotations.SerializedName;

public class FFAPI {
	private static final String API_URL = "http://friendfeed-api.com/v2";
	private static final String API_KEY = "c914bd31ea024b9bade1365cefa8b989";
	
	// private static final String API_SEC = "d5d5e78a0ced4a1da49230fe09696353078d9f37b0a841a888e24c064e88212d";
	
	public interface FF {
		
		// async
		
		@GET("/feedinfo/{feed_id}")
		void get_profile(@EncodedPath("feed_id") String feed_id, Callback<FeedInfo> callback);
		
		@GET("/feedlist")
		void get_navigation(Callback<FeedList> callback);
		
		@GET("/short/{short_url}")
		void rev_short(@Path("short_url") String short_url, Callback<Entry> callback);
		
		@GET("/entry/{entry_id}")
		void get_entry_async(@EncodedPath("entry_id") String entry_id, Callback<Entry> callback);
		
		@POST("/short")
		void get_short(@Query("entry") String entry_id, Callback<Entry> callback);
		
		@POST("/entry")
		void ins_entry(@Body MultipartTypedOutput mto, Callback<Entry> callback);
		
		@POST("/entry/delete")
		void del_entry(@Query("id") String entry_id, Callback<Entry> callback);
		
		@POST("/entry/delete")
		void und_entry(@Query("id") String entry_id, @Query("undelete") int undelete, Callback<Entry> callback);
		
		@POST("/hide")
		void set_hidden(@Query("entry") String entry_id, Callback<Entry> callback);
		
		@POST("/hide")
		void set_unhide(@Query("entry") String entry_id, @Query("unhide") int unhide, Callback<Entry> callback);
		
		@POST("/like")
		void ins_like(@Query("entry") String entry_id, Callback<Like> callback);
		
		@POST("/like/delete")
		void del_like(@Query("entry") String entry_id, Callback<SimpleResponse> callback);
		
		@POST("/comment")
		void ins_comment(@Query("entry") String entry_id, @Query("body") String body, Callback<Comment> callback);
		
		@POST("/comment")
		void upd_comment(@Query("entry") String entry_id, @Query("id") String comm_id, @Query("body") String body,
			Callback<Comment> callback);
		
		@POST("/comment/delete")
		void del_comment(@Query("id") String comm_id, Callback<Comment> callback);
		
		@POST("/comment/delete")
		void und_comment(@Query("id") String comm_id, @Query("undelete") int undelete, Callback<Comment> callback);
		
		@POST("/subscribe")
		void subscribe(@Query("feed") String feed, @Query("list") String list, Callback<SimpleResponse> callback);
		
		@POST("/unsubscribe")
		void unsubscribe(@Query("feed") String feed, @Query("list") String list, Callback<SimpleResponse> callback);
		
		// sync
		
		@GET("/feedinfo/{feed_id}")
		FeedInfo get_profile_sync(@Path("feed_id") String feed_id);
		
		@GET("/feedlist")
		FeedList get_navigation_sync();
		
		@GET("/feed/{feed_id}")
		Feed get_feed_normal(@EncodedPath("feed_id") String feed_id, @Query("start") int start, @Query("num") int num);
		
		@GET("/updates/feed/{feed_id}")
		Feed get_feed_updates(@EncodedPath("feed_id") String feed_id, @Query("num") int num,
			@Query("cursor") String cursor, @Query("timeout") int timeout, @Query("updates") int updates);
		
		@GET("/search")
		Feed get_search_normal(@Query("q") String query, @Query("start") int start, @Query("num") int num);
		
		@GET("/updates/search")
		Feed get_search_updates(@Query("q") String query, @Query("num") int num, @Query("cursor") String cursor,
			@Query("timeout") int timeout, @Query("updates") int updates);
		
		@GET("/entry/{entry_id}")
		Entry get_entry(@EncodedPath("entry_id") String entry_id);
	}
	
	public static class SimpleResponse {
		public boolean success = false; // unlike & unsubscribe?
		public String status = ""; // subscribe & unsubscribe?
	}
	
	static class IdentItem {
		public static String accountID = "";
		public static String accountName = "You";
		public static String accountFeed = "Your feed";
		
		public String id = "";
		public List<String> commands = new ArrayList<String>();
		public long timestamp = System.currentTimeMillis();
		
		public long getAge() {
			return System.currentTimeMillis() - timestamp;
		}
		
		public boolean isIt(String checkId) {
			return id.trim().toLowerCase(Locale.getDefault()).equals(checkId.trim().toLowerCase(Locale.getDefault()));
		}
	}
	
	public static class BaseFeed extends IdentItem {
		public String name;
		public String type = "";
		public String description;
		
		@SerializedName("private")
		public Boolean locked = false;
		
		public boolean isMe() {
			return !TextUtils.isEmpty(accountID) && isIt(accountID);
		}
		
		public boolean isHome() {
			return isIt("home");
		}
		
		public boolean isList() {
			return type.equals("special") && id.startsWith("list/");
		}
		
		public boolean isUser() {
			return type.equals("user");
		}
		
		public boolean isGroup() {
			return type.equals("group");
		}
		
		public boolean canPost() {
			return commands.contains("post");
		}
		
		public boolean canDM() {
			return commands.contains("dm");
		}
		
		public boolean canSetSubscriptions() {
			return isList() || (commands.contains("subscribe") || commands.contains("unsubscribe"));
		}
		
		public boolean isSubscribed() {
			return commands.contains("unsubscribe");
		}
		
		public String getName() {
			return isMe() ? accountName : name.trim();
		}
		
		public String getAvatarUrl() {
			return "http://friendfeed-api.com/v2/picture/" + id + "?size=large";
		}
	}
	
	public static class Feed extends BaseFeed {
		public List<Entry> entries = new ArrayList<Entry>();
		public Realtime realtime;
		
		public static String lastDeletedEntry = "";
		
		public int indexOf(String eid) {
			for (int i = 0; i < entries.size(); i++)
				if (entries.get(i).isIt(eid))
					return i;
			return -1;
		}
		
		public Entry find(String eid) {
			for (Entry e : entries)
				if (e.id.equals(eid))
					return e;
			return null;
		}
		
		public int append(Feed feed) {
			int res = 0;
			for (Entry e : feed.entries)
				if (find(e.id) == null) {
					entries.add(e);
					e.checkLocalHide();
					res++;
				}
			return res;
		}
		
		public int update(Feed feed, boolean sortLikeSite) {
			realtime = feed.realtime;
			timestamp = feed.timestamp;
			int res = 0;
			for (Entry e : feed.entries)
				if (e.created) {
					entries.add(0, e);
					e.checkLocalHide();
					res++;
				} else
					for (Entry old : entries)
						if (old.isIt(e.id)) {
							old.update(e);
							break;
						}
			if (!TextUtils.isEmpty(lastDeletedEntry))
				for (int n = 0; n < entries.size(); n++)
					if (entries.get(n).id.equals(lastDeletedEntry)) {
						entries.remove(n);
						lastDeletedEntry = "";
						break;
					}
			if (sortLikeSite && res > 0) {
				Collections.sort(entries, new Comparator<BaseEntry>() {
				    @Override
				    public int compare(BaseEntry o1, BaseEntry o2) {
				        return o1.date.compareTo(o2.date) * -1;
				    }
				});
			}
			return res;
		}
		
		public int update(Feed feed) {
			return update(feed, false);
		}
		
		public void checkLocalHide() {
			for (Entry e : entries)
				e.checkLocalHide();
		}
		
		public static class Realtime {
			public String cursor;
			public final long timestamp = System.currentTimeMillis();
		}
	}
	
	public static class BaseEntry extends IdentItem {
		public BaseFeed from;
		public Date date;
		public String body;
		public String rawBody;
		public Origin via;
		public boolean created;
		public boolean updated;
		public boolean banned = false; // local
		public boolean spoiler = false; // local
		
		public boolean isMine() {
			return from.isMe();
		}
		
		public boolean canEdit() {
			return commands.contains("edit");
		}
		
		public boolean canDelete() {
			return commands.contains("delete");
		}
		
		public String getFuzzyTime() {
			return DateUtils.getRelativeTimeSpanString(date.getTime(), System.currentTimeMillis(),
				DateUtils.MINUTE_IN_MILLIS).toString();
		}
		
		public String getFirstUrl() {
			String res = Commons.firstUrl(rawBody);
			if (res == null)
				res = Commons.firstUrl(body);
			return res;
		}
		
		public String getFirstImage() {
			String res = Commons.firstImage(rawBody);
			if (res == null)
				res = Commons.firstImage(body);
			return res;
		}
		
		public void update(BaseEntry item) {
			if (!isIt(item.id))
				return;
			timestamp = item.timestamp;
			commands = item.commands;
			from = item.from; // people and rooms can change name/avatar at any time.
			via = item.via; // barely useless.
			if (item.updated) {
				date = item.date;
				body = item.body;
				rawBody = item.rawBody;
				updated = true;
			}
			checkLocalHide();
		}
		
		public void checkLocalHide() {
			banned = false;
			spoiler = false;
			try {
				String chk = !TextUtils.isEmpty(body) ? body.toLowerCase(Locale.getDefault()).trim() : "";
				chk += !TextUtils.isEmpty(chk) ? "\n\n\n" : "";
				chk += !TextUtils.isEmpty(rawBody) ? rawBody.toLowerCase(Locale.getDefault()).trim() : "";
				if (!TextUtils.isEmpty(chk)) {
					if (!isMine())
						for (String bw: Commons.bWords)
							if (chk.indexOf(bw) >= 0) {
								banned = true;
								break;
							}
					spoiler = Commons.bSpoilers && chk.contains("#spoiler");
				}
			} catch (Exception err) {
			}
		}
		
		public static class Origin {
			public String name;
			public String url;
		}
	}
	
	public static class Entry extends BaseEntry {
		public String rawLink;
		public String url;
		public List<Comment> comments = new ArrayList<Comment>();
		public List<Like> likes = new ArrayList<Like>();
		public BaseFeed[] to = new BaseFeed[] {};
		public Thumbnail[] thumbnails = new Thumbnail[] {};
		public Fof fof;
		public String fofHtml;
		public String shortId = "";
		public String shortUrl = "";
		public Attachment[] files = new Attachment[] {};
		public Coordinates geo;
		public boolean hidden = false; // undocumented
		public int thumbpos = 0; // local
		
		@Override
		public void update(BaseEntry item) {
			super.update(item);
			
			if (!isIt(item.id))
				return;
			
			Entry entry = (Entry) item;

			url = entry.url;
			fof = entry.fof;
			hidden = entry.hidden;
			rawLink = entry.rawLink;
			fofHtml = entry.fofHtml;
			thumbnails = entry.thumbnails;
			files = entry.files;
			geo = entry.geo;

			for (Comment comm : entry.comments)
				if (comm.created)
					comments.add(comm);
				else
					for (Comment old : comments)
						if (old.isIt(comm.id)) {
							old.update(comm);
							break;
						}
			
			for (Like like : entry.likes)
				if (like.created)
					likes.add(like);
			
			updated = true;
		}
		
		@Override
		public void checkLocalHide() {
			super.checkLocalHide();
			
			if (canUnlike()) {
				banned = false;
				spoiler = false;
			} else
				for (BaseFeed bf: to)
					if (Commons.bFeeds.contains(bf.id)) {
						banned = true;
						break;
					}
			for (Comment c: comments)
				c.checkLocalHide();
		}
		
		public boolean isDM() {
			if (to.length <= 0)
				return false;
			for (BaseFeed f: to)
				if (!f.isUser() || f.isIt(from.id))
					return false;
			return true;
		}
		
		public boolean canComment() {
			return commands.contains("comment");
		}
		
		public boolean canLike() {
			return commands.contains("like");
		}
		
		public boolean canUnlike() {
			return commands.contains("unlike") || hidden;
		}
		
		public boolean canHide() {
			return commands.contains("hide");// || !hidden;
		}
		
		public boolean canUnhide() {
			return commands.contains("unhide");
		}
		
		public String getToLine() {
			if (to.length <= 0 || to.length == 1 && (to[0].id.equals(from.id) || to[0].id.equals(id)))
				return null;
			List<String> lst = new ArrayList<String>();
			for (BaseFeed f : to)
				lst.add(f.isMe() ? IdentItem.accountFeed : f.getName());
			return TextUtils.join(", ", lst);
		}
		
		public String[] getFofIDs() {
			if (fof == null || TextUtils.isEmpty(fofHtml))
				return null;
			Pattern p = Pattern.compile("friendfeed\\.com/((\\d|\\w)+)");
			Matcher m = p.matcher(fofHtml);
			String f1 = null;
			String f2 = null;
			if (m.find()) {
				f1 = m.group(1);
				if (m.find())
					f2 = m.group(1);
			}
			if (f2 != null)
				return new String[] { f1, f2 };
			if (f1 != null)
				return new String[] { f1 };
			return null;
		}
		
		public int getFilesCount() {
			return files.length + thumbnails.length;
		}
		
		public int getLikesCount() {
			if (likes == null)
				return 0;
			for (Like l : likes)
				if (l.placeholder != null && l.placeholder)
					return l.num + likes.size() - 1;
			return likes.size();
		}
		
		public int getCommentsCount() {
			if (comments == null)
				return 0;
			for (Comment c : comments)
				if (c.placeholder != null && c.placeholder)
					return c.num + comments.size() - 1;
			return comments.size();
		}
		
		public String[] getMediaUrls(boolean attachments) {
			String[] res = new String[attachments ? thumbnails.length + files.length : thumbnails.length];
			for (int i = 0; i < thumbnails.length; i++)
				res[i] = thumbnails[i].link;
			if (attachments)
				for (int i = 0; i < thumbnails.length; i++)
					res[thumbnails.length + i] = files[i].url;
			return res;
		}
		
		public int indexOfComment(String cid) {
			Comment c;
			for (int i = 0; i < comments.size(); i++) {
				c = comments.get(i);
				if (!c.placeholder && c.id.equals(cid))
					return i;
			}
			return -1;
		}
		
		public int indexOfLike(String userId) {
			Like l;
			for (int i = 0; i < likes.size(); i++) {
				l = likes.get(i);
				if (!l.placeholder && l.from.id.equals(userId))
					return i;
			}
			return -1;
		}
		
		public boolean hasSpoilers() {
			for (Comment c: comments)
				if (c.spoiler)
					return true;
			return false;
		}
		
		public boolean hasReplies() {
			for (Comment c: comments)
				if ((c.created || c.updated) && !c.from.isMe())
					return true;
			return false;
		}
		
		public void thumbNext() {
			thumbpos = thumbnails.length > 0 ? (thumbpos + 1) % thumbnails.length : 0;
		}
		
		public void thumbPrior() {
			thumbpos = thumbnails.length > 0 ? (thumbpos + (thumbnails.length - 1)) % thumbnails.length : 0;
		}
		
		public static class Fof {
			public String type;
			public BaseFeed from;
		}
		
		public static class Thumbnail {
			public String url = "";
			public String link = "";
			public int width = 0;
			public int height = 0;
			public String player = "";
			public int rotation = 0; // local
			public boolean landscape = true; // local
			public String videoId = null; // local
			public String videoUrl = null; // local
			
			public boolean isFFMediaPic() {
				return link.indexOf("/m.friendfeed-media.com/") > 0;
			}
			
			public boolean isSimplePic() {
				return link.endsWith(".jpg") || link.endsWith(".jpeg") || link.endsWith(".png") || link.endsWith(".gif");
			}
			
			public boolean isYouTube() {
				if (TextUtils.isEmpty(player))
					return false;
				if (!(TextUtils.isEmpty(videoId) || TextUtils.isEmpty(videoUrl)))
					return true;
				Document doc = Jsoup.parseBodyFragment(player);
				Elements emb = doc.getElementsByTag("embed");
				if (emb != null && emb.size() > 0 && emb.get(0).hasAttr("src")) {
					String src = emb.get(0).attr("src");
					if (Commons.YouTube.isVideoUrl(src)) {
						videoId = Commons.YouTube.getId(src);
						videoUrl = Commons.YouTube.getFriendlyUrl(src);
						return true;
					}
				}
				return false;
			}
			
			public String videoPreview() {
				return isYouTube() ? Commons.YouTube.getPreview(videoUrl) : null;
			}
		}
		
		public static class Attachment {
			public String url;
			public String type;
			public String name;
			public String icon;
			public int size = 0;
		}
		
		public static class Coordinates {
			public String lat;
			
			@SerializedName("long")
			public String lon;
		}
	}
	
	public static class Comment extends BaseEntry {
		// compact view only (plus body):
		public Boolean placeholder = false;
		public int num;
		
		@Override
		public void checkLocalHide() {
			if (placeholder)
				return;
			super.checkLocalHide();
			if (body.toLowerCase(Locale.getDefault()).equals("sp") ||
				body.toLowerCase(Locale.getDefault()).equals("spoiler") ||
				rawBody.toLowerCase(Locale.getDefault()).equals("sp") ||
				rawBody.toLowerCase(Locale.getDefault()).equals("spoiler"))
				spoiler = true;
		}
	}
	
	public static class Like {
		public Date date;
		public BaseFeed from;
		public boolean created;
		public boolean updated;
		// compact view only:
		public String body;
		public Boolean placeholder = false;
		public int num;
		
		public boolean isMine() {
			return from.isMe();
		}
		
		public String getFuzzyTime() {
			return DateUtils.getRelativeTimeSpanString(date.getTime(), System.currentTimeMillis(),
				DateUtils.MINUTE_IN_MILLIS).toString();
		}
	}
	
	public static class FeedInfo extends BaseFeed {
		public BaseFeed[] feeds = new BaseFeed[] {}; // lists only
		public BaseFeed[] subscriptions = new BaseFeed[] {}; // users only
		public BaseFeed[] subscribers = new BaseFeed[] {}; // users and groups
		public BaseFeed[] admins = new BaseFeed[] {}; // groups only
		
		public BaseFeed findFeedById(String fid) {
			for (BaseFeed f: subscriptions)
				if (f.isIt(fid))
					return f;
			for (BaseFeed f: subscribers)
				if (f.isIt(fid))
					return f;
			return null;
		}
	}
	
	public static class FeedList {
		public SectionItem[] main;
		public SectionItem[] lists;
		public SectionItem[] groups;
		public SectionItem[] searches;
		public Section[] sections;
		public long timestamp = System.currentTimeMillis();
		
		public long getAge() {
			return System.currentTimeMillis() - timestamp;
		}
		
		public SectionItem getSectionByFeed(String feed_id) {
			for (Section s : sections)
				for (SectionItem f : s.feeds)
					if (f.id.equals(feed_id))
						return f;
			return null;
		}
		
		public static class SectionItem extends BaseFeed {
			public String query = "";
		}
		
		public static class Section {
			public String id;
			public String name;
			public SectionItem[] feeds;
			
			public boolean hasFeed(String feed_id) {
				for (SectionItem si : feeds)
					if (si.id.equals(feed_id))
						return true;
				return false;
			}
		}
	}
	
	public static void dropClients() {
		CLIENT_PROFILE = null;
		CLIENT_MSGS = null;
		CLIENT_FEED = null;
		CLIENT_ENTRY = null;
		CLIENT_WRITER = null;
	}
	
	private static FF CLIENT_PROFILE;
	private static FF CLIENT_MSGS;
	private static FF CLIENT_FEED;
	private static FF CLIENT_ENTRY;
	private static FF CLIENT_WRITER;
	
	public static FF client_profile(final FFSession session) {
		if (CLIENT_PROFILE == null)
			CLIENT_PROFILE = new RestAdapter.Builder().setEndpoint(API_URL).setRequestInterceptor(
				new RequestInterceptor() {
					@Override
					public void intercept(RequestFacade request) {
						String authText = session.getUsername() + ":" + session.getRemoteKey();
						String authData = "Basic " + Base64.encodeToString(authText.getBytes(), 0);
						request.addHeader("Authorization", authData);
						request.addHeader("User-Agent", Commons.USER_AGENT);
						request.addQueryParam("locale", session.getPrefs().getString(PK.LOCALE, "en"));
					}
				}).setLogLevel(RestAdapter.LogLevel.NONE).setClient(new WaitingUCC()).build().create(FF.class);
		return CLIENT_PROFILE;
	}
	
	public static FF client_msgs(final FFSession session) {
		if (CLIENT_MSGS == null)
			CLIENT_MSGS = new RestAdapter.Builder().setEndpoint(API_URL).setRequestInterceptor(
				new RequestInterceptor() {
					@Override
					public void intercept(RequestFacade request) {
						String authText = session.getUsername() + ":" + session.getRemoteKey();
						String authData = "Basic " + Base64.encodeToString(authText.getBytes(), 0);
						request.addHeader("Authorization", authData);
						request.addHeader("User-Agent", Commons.USER_AGENT);
						request.addQueryParam("locale", session.getPrefs().getString(PK.LOCALE, "en"));
						request.addQueryParam("maxcomments", "1000");
						request.addQueryParam("raw", "1");
					}
				}).setLogLevel(RestAdapter.LogLevel.NONE).setClient(new WaitingUCC()).build().create(FF.class);
		return CLIENT_MSGS;
	}
	
	public static FF client_feed(final FFSession session) {
		if (CLIENT_FEED == null)
			CLIENT_FEED = new RestAdapter.Builder().setEndpoint(API_URL).setRequestInterceptor(
				new RequestInterceptor() {
					@Override
					public void intercept(RequestFacade request) {
						String authText = session.getUsername() + ":" + session.getRemoteKey();
						String authData = "Basic " + Base64.encodeToString(authText.getBytes(), 0);
						request.addHeader("Authorization", authData);
						request.addHeader("User-Agent", Commons.USER_AGENT);
						request.addQueryParam("locale", session.getPrefs().getString(PK.LOCALE, "en"));
						request.addQueryParam("maxcomments", "auto");
						request.addQueryParam("maxlikes", "auto");
						request.addQueryParam("raw", "1");
						if (session.getPrefs().getBoolean(PK.FEED_FOF, true))
							request.addQueryParam("fof", "1");
						if (session.getPrefs().getBoolean(PK.FEED_HID, true))
							request.addQueryParam("hidden", "1");
					}
				}).setLogLevel(RestAdapter.LogLevel.NONE).setClient(new WaitingUCC()).build().create(FF.class);
		return CLIENT_FEED;
	}
	
	public static FF client_entry(final FFSession session) {
		if (CLIENT_ENTRY == null)
			CLIENT_ENTRY = new RestAdapter.Builder().setEndpoint(API_URL).setRequestInterceptor(
				new RequestInterceptor() {
					@Override
					public void intercept(RequestFacade request) {
						String authText = session.getUsername() + ":" + session.getRemoteKey();
						String authData = "Basic " + Base64.encodeToString(authText.getBytes(), 0);
						request.addHeader("Authorization", authData);
						request.addHeader("User-Agent", Commons.USER_AGENT);
						request.addQueryParam("locale", session.getPrefs().getString(PK.LOCALE, "en"));
						request.addQueryParam("raw", "1");
					}
				}).setLogLevel(RestAdapter.LogLevel.NONE).setClient(new WaitingUCC()).build().create(FF.class);
		return CLIENT_ENTRY;
	}
	
	public static FF client_write(final FFSession session) {
		if (CLIENT_WRITER == null)
			CLIENT_WRITER = new RestAdapter.Builder().setEndpoint(API_URL).setRequestInterceptor(
				new RequestInterceptor() {
					@Override
					public void intercept(RequestFacade request) {
						String authText = session.getUsername() + ":" + session.getRemoteKey();
						String authData = "Basic " + Base64.encodeToString(authText.getBytes(), 0);
						request.addHeader("Authorization", authData);
						request.addQueryParam("appid", API_KEY);
					}
				}).setLogLevel(RestAdapter.LogLevel.NONE).setClient(new WaitingUCC()).build().create(FF.class);
		return CLIENT_WRITER;
	}
	
	static class WaitingUCC extends UrlConnectionClient {
		@Override
		protected HttpURLConnection openConnection(Request request) throws IOException {
			HttpURLConnection connection = super.openConnection(request);
			connection.setConnectTimeout(20 * 1000); // 20 sec
			connection.setReadTimeout(60 * 1000); // 60 sec
			return connection;
		}
	}
}