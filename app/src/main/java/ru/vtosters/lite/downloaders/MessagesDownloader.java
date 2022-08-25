package ru.vtosters.lite.downloaders;
import static ru.vtosters.lite.encryption.EncryptProvider.decryptMessage;
import static ru.vtosters.lite.net.Request.makeRequest;
import static ru.vtosters.lite.proxy.ProxyUtils.getApi;
import static ru.vtosters.lite.utils.AccountManagerUtils.getUserId;
import static ru.vtosters.lite.utils.AccountManagerUtils.getUserToken;
import static ru.vtosters.lite.utils.AndroidUtils.getGlobalContext;
import static ru.vtosters.lite.utils.AndroidUtils.getResources;

import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class MessagesDownloader{
    private static SimpleDateFormat sdformat;

    private static SparseArray<MiniUser> usersArray;

    public static String formatTime(long now){
        return sdformat.format(new Date(now));
    }

    public static String join(CharSequence delimiter, Iterable<? extends CharSequence> elements){
        Objects.requireNonNull(delimiter);
        Objects.requireNonNull(elements);

        StringBuilder builder = new StringBuilder();

        for (CharSequence cs : elements) {
            builder.append(cs).append(delimiter);
        }

        return builder.toString();
    }

    public void downloadDialog(int peer_id, DialogDownloaderFormatProvider format, File out) throws Exception{
        sdformat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", getResources().getConfiguration().locale);

        FileOutputStream fos = new FileOutputStream(out);

        // fix TODO with conversation name (API method messages.getConversationsById)
        fos.write(format.provideDocumentStart("TODO", formatTime(System.currentTimeMillis())).getBytes(StandardCharsets.UTF_8));

        fetchDialogInfo(peer_id, 0, ((users, messages) -> {
            try {
                fos.write(format.provideHeader("TODO", formatTime(System.currentTimeMillis())).getBytes(StandardCharsets.UTF_8));
                for (MiniMsg msg : messages) {
                    fos.write(format.provideMessage(msg, users.get(msg.fromId)).getBytes(StandardCharsets.UTF_8));
                }
                fos.write(format.provideDocumentEnd().getBytes(StandardCharsets.UTF_8));
                fos.close();
                Toast.makeText(getGlobalContext(), "Сохранено как " + out.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }));
    }

    public void fetchDialogInfo(int peer_id, int offset, FetchListener fl){
        makeRequest("https://" + getApi() + "/method/messages.getHistory?extended=1&v=5.140&offset=" + offset + "&count=200&peer_id=" + peer_id + "&access_token=" + getUserToken(),
                response -> {
                    try {
                        JSONObject mainJson = new JSONObject(response);
                        JSONObject obj = mainJson.getJSONObject("response");

                        SparseArray<MiniUser> users = new SparseArray<>();
                        parseUsers(users, obj.optJSONArray("profiles"), false);
                        parseUsers(users, obj.optJSONArray("groups"), true);

                        List<MiniMsg> msgs = parseMessages(obj.getJSONArray("items"));

                        usersArray = users;

                        fl.onReceive(users, msgs);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });
    }

    private List<MiniMsg> parseMessages(JSONArray jsonArray) throws JSONException{
        ArrayList<MiniMsg> thing = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            MiniMsg msg = new MiniMsg(jsonArray.getJSONObject(i));
            thing.add(msg);
        }

        return thing;
    }

    private void parseUsers(SparseArray<MiniUser> array, @Nullable JSONArray jsonArray, boolean groups) throws JSONException{
        if (jsonArray == null) return;

        for (int i = 0; i < jsonArray.length(); i++) {
            MiniUser user = new MiniUser(jsonArray.getJSONObject(i));
            array.put(groups ? -user.id : user.id, user);
        }
    }

    interface FetchListener{
        void onReceive(SparseArray<MiniUser> users, List<MiniMsg> messages);
    }

    static class MiniUser{
        public String firstName;
        public String lastName;
        int id;
        String photo100;

        public MiniUser(JSONObject obj) throws JSONException {
            id = obj.getInt("id");
            firstName = obj.optString("first_name", obj.optString("name"));
            lastName = obj.optString("last_name", "");
            photo100 = obj.getString("photo_100");
        }
    }

    static class MiniMsg{
        long date;
        int fromId;
        int id; // message id
        String text;
        List<JSONObject> attachments;
        List<JSONObject> fwd_messages;
        JSONObject reply_message; // one reply
        JSONObject chat_action;

        public MiniMsg(JSONObject obj) throws JSONException{
            date = obj.getLong("date") * 1000;
            fromId = obj.getInt("from_id");
            id = obj.getInt("id");
            text = decryptMessage(obj.getString("text"), obj.getInt("peer_id"));
            reply_message = obj.optJSONObject("reply_message");
            chat_action = obj.optJSONObject("action");

            attachments = new ArrayList<>();
            fwd_messages = new ArrayList<>();

            JSONArray arr = obj.optJSONArray("attachments");
            if (arr != null)
                for (int i = 0; i < arr.length(); i++) attachments.add(arr.getJSONObject(i));

            JSONArray fwdArr = obj.optJSONArray("fwd_messages");
            if (fwdArr != null)
                for (int i = 0; i < fwdArr.length(); i++) fwd_messages.add(fwdArr.getJSONObject(i));
        }
    }

    // This class defines everything which is needed to create an output file
    abstract static class DialogDownloaderFormatProvider{
        // Internal, for JSON/HTML
        abstract String provideDocumentStart(String dialogName, String date);

        abstract String provideDocumentEnd();

        abstract String provideHeader(String dialogName, String date);

        abstract String provideMessage(MiniMsg message, MiniUser user) throws JSONException;
    }

    public static class HtmlDialogDownloaderFormatProvider extends DialogDownloaderFormatProvider{
        @Override
        String provideDocumentStart(String dialogName, String date){
            String style = ".vtex-milk-msg {\n" +
                    "    border-radius: 8px;\n" +
                    "    padding: 24px;\n" +
                    "    background: white;\n" +
                    "    margin: 16px;\n" +
                    "    box-shadow: 0 4px 8px 0 rgba(0,0,0,0.07);\n" +
                    "    word-wrap: break-word;\n" +
                    "}\n" +
                    "\n" +
                    ".vtex-milk-msgcont br {\n" +
                    "    display: none;\n" +
                    "}\n" +
                    "\n" +
                    ".vtex-milk-msg p {\n" +
                    "    margin: 0;\n" +
                    "}\n" +
                    "\n" +
                    ".vtex-milk-msg.vtex-milk-msg-out {\n" +
                    "    background: #ADD3FF;\n" +
                    "}\n" +
                    "\n" +
                    "p.msg-attaches {\n" +
                    "    margin-top: 8px;\n" +
                    "    font-style: italic;\n" +
                    "}\n" +
                    "p.chat-action {\n" +
                    "    font-style: italic;\n" +
                    "}\n" +
                    "\n" +
                    ".vtex-milk-header {\n" +
                    "    border-radius: 8px;\n" +
                    "    padding: 24px;\n" +
                    "    background: white;\n" +
                    "    margin: 16px;\n" +
                    "    box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2);\n" +
                    "}\n" +
                    "\n" +
                    "h4.vtex-milk-header-txt {\n" +
                    "    margin: 0;\n" +
                    "}\n" +
                    "\n" +
                    "h5.vtex-milk-header-sub {\n" +
                    "    margin: 0;\n" +
                    "    padding-top: 8px;\n" +
                    "}\n" +
                    "\n" +
                    "a.msg-attach-link {\n" +
                    "    color: #5181B8;\n" +
                    "    text-decoration: none;\n" +
                    "}\n" +
                    "\n" +
                    ".msg-from a {\n" +
                    "    color: #5181B8;\n" +
                    "    text-decoration: none;\n" +
                    "}\n" +
                    "\n" +
                    ".msg-from-date {\n" +
                    "    color: rgba(0, 0, 0, 0.75);\n" +
                    "}\n" +
                    "\n" +
                    "body {\n" +
                    "    font-family: Roboto, system-ui, sans-serif;\n" +
                    "}\n" +
                    "\n" +
                    ".msg-body {\n" +
                    "    padding-top: 4px;\n" +
                    "}\n" +
                    ".msg-reply {\n" +
                    "   border-left: 3px solid #7aa7f6;\n" +
                    "   margin-top: 5px;\n" +
                    "   padding-left: 3px;\n" +
                    "}\n\n" +
                    ".msg-reply p {\n" +
                    "   padding-left: 5px;\n" +
                    "}\n\n" +
                    ".msg-reply p.reply-header {\n" +
                    "   color: #5181B8;\n" +
                    "}\n\n" +
                    ".msg-reply p.reply-content {\n" +
                    "   color: #000000;\n" +
                    "}\n" +
                    "\n" +
                    "";

            return "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta charset=\"utf-8\"><title>Экспорт из VTLite</title><style>" + style + "</style></head><body>";
        }

        @Override
        String provideDocumentEnd(){
            return "</div></body></html>";
        }

        @Override
        String provideHeader(String dialogName, String date){
            return "<div class=\"vtex-milk-header\"><h4 class=\"vtex-milk-header-txt\">VTLite</h4><h5 class=\"vtex-milk-header-sub\">Экспорт истории чата (" + date + ")</h5></div><div class=\"vtex-milk-msgcont\">";
        }

        @Override
        String provideMessage(MiniMsg message, MiniUser user) throws JSONException{
            String isOut = (user.id == getUserId()) ? " vtex-milk-msg-out" : "";
            String chatAction = (message.chat_action != null) ? getHtmlForChatAction(message.chat_action) : "";
            String attaches = (message.attachments.size() > 0) ? "<p class=\"msg-attaches\">Вложения: " + getHtmlForAttach(message.attachments) + "</p>" : "";
            String reply = (message.reply_message != null) ? provideReply(message.reply_message) : "";
            String fwdMessages = (message.fwd_messages.size() > 0) ? provideForwardMessages(message.fwd_messages) : "";

            return "<div class=\"vtex-milk-msg" + isOut + "\" id=\"" + message.id + "\">" +
                        "<p class=\"msg-from\">от <a href=\"https://vk.ru/id" + user.id + "\">" + user.firstName + " " + user.lastName + "</a> <span class=\"msg-from-date\">" + formatTime(message.date) + "</span></p>" + // TODO photo100
                        "<p class=\"msg-body\">" + mentionsReplace(message.text) + "</p>" +
                        chatAction +
                        attaches +
                        reply +
                        fwdMessages +
                    "</div>";
        }

        private String provideReply(JSONObject replyMessage) throws JSONException {
            var messageId = replyMessage.getInt("id");
            var userId = replyMessage.getInt("from_id");
            var peerId = replyMessage.getInt("peer_id");
            var text = mentionsReplace(decryptMessage(replyMessage.getString("text"), peerId));
            var fwdMessages = replyMessage.optJSONArray("fwd_messages");

            var user = usersArray.get(userId);

            if (text.isEmpty()) {
                if (fwdMessages != null) {
                    var length = fwdMessages.length();
                    if (length > 0) text = length + " " + pluralMsg(length);
                } else text = "Вложение";
            }

            return "<a href=\"#" + messageId + "\" class=\"msg-attach-link\">" +
                        "<div class=\"msg-reply\">" +
                            "<p class=\"reply-header\">Пересланное сообщение</p>" +
                            "<p class=\"reply-content\">" + user.firstName + " " + user.lastName + ": " + text + "</p>" +
                        "</div>" +
                    "</a>";
        }

        private String provideForwardMessages(List<JSONObject> fwd_messages) throws JSONException {
            StringBuilder fwdmsgs = new StringBuilder("<div class=\"msg-reply\">");

            for (JSONObject fwd_message : fwd_messages) {
                List<JSONObject> attaches = new ArrayList<>();

                JSONArray arr = fwd_message.optJSONArray("attachments");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            attaches.add(arr.getJSONObject(i));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }

                List<JSONObject> fwdsMsg = new ArrayList<>();

                JSONArray fwd_msgs = fwd_message.optJSONArray("fwd_messages");
                if (fwd_msgs != null) {
                    for (int i = 0; i < fwd_msgs.length(); i++) {
                        try {
                            fwdsMsg.add(fwd_msgs.getJSONObject(i));
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                }

                JSONObject reply_message = fwd_message.optJSONObject("reply_message");

                var attachesHtml = (attaches.size() > 0) ? "Вложения: " + getHtmlForAttach(attaches) : "";
                var replyHtml = (reply_message != null) ? provideReply(reply_message) : "";
                var fwdsHtml = (fwdsMsg.size() > 0) ? provideForwardMessages(fwdsMsg) : ""; // recursion XD :>

                var userId = fwd_message.getInt("from_id");
                var peerId = fwd_message.getInt("peer_id");
                var text = mentionsReplace(decryptMessage(fwd_message.getString("text"), peerId));

                var userLink = ((userId < 0) ? "club" : "id") + Math.abs(userId);

                fwdmsgs.append("<p class=\"reply-content\"><a class=\"msg-attach-link\" href=\"https://vk.ru/").append(userLink).append("\">@").append(userLink).append("</a>: ").append(text).append(attachesHtml).append(replyHtml).append(fwdsHtml);
            }

            return fwdmsgs.append("</div>").toString();
        }

        private String mentionsReplace(String text) {
            var matcher = Pattern.compile("\\[((?:club|id|public)\\d+)\\|([^]]+)\\]").matcher(text);

            return matcher.replaceAll("<a class=\"msg-attach-link\" href=\"https://vk.ru/$1\">$2</a>");
        }

        private String pluralMsg(int count) {
            String[] plurals = {"сообщение", "сообщения", "сообщений"};

            var hundredModulo = count % 100;
            var tenModulo = count % 10;

            if (hundredModulo > 10 && hundredModulo < 20) return plurals[2];
            else if (tenModulo > 1 && tenModulo < 5) return plurals[1];
            else if (tenModulo == 1) return plurals[0];

            return plurals[2];
        }

        private String getHtmlForChatAction(JSONObject action) {
            var type = action.optString("type");

            StringBuilder htmlAction = new StringBuilder("<p class=\"chat-action\">");

            switch(type) {
                case "chat_photo_update":
                    htmlAction.append("Обновил фотографию беседы");
                    break;
                case "chat_photo_remove":
                    htmlAction.append("Удалил фотографию беседы");
                    break;
                case "chat_create":
                    var name = action.optString("text");
                    htmlAction.append("Создал беседу").append((!name.isEmpty()) ? " с названием \"" + name + "\"" : "");
                    break;
                case "chat_title_update":
                    var newName = action.optString("text");
                    htmlAction.append("Сменил название беседы").append((!newName.isEmpty()) ? " на \"" + newName : "\"");
                    break;
                case "chat_invite_user":
                    var newUserId = action.optInt("member_id");
                    htmlAction.append("Добавил " + ((newUserId > 0) ? "пользователя" : "группу"));
                    if (newUserId != 0) {
                        var userLink = ((newUserId < 0) ? "club" : "id") + Math.abs(newUserId);
                        htmlAction.append(" <a href=\"https://vk.ru/").append(userLink).append("\" class=\"msg-attach-link\">@").append(userLink).append("</a>");
                    }
                    break;
                case "chat_kick_user":
                    var removedUserId = action.optInt("member_id");
                    htmlAction.append("Исключил " + ((removedUserId > 0) ? "пользователя" : "группу"));
                    if (removedUserId != 0) {
                        var userLink = ((removedUserId < 0) ? "club" : "id") + Math.abs(removedUserId);
                        htmlAction.append(" <a href=\"https://vk.ru/").append(userLink).append("\" class=\"msg-attach-link\">@").append(userLink).append("</a>");
                    }
                    break;
                case "chat_pin_message":
                    var message = action.optString("message");
                    htmlAction.append("Закрепил сообщение").append((!message.isEmpty()) ? (" с текстом: \"" + message + "\"") : "");
                    break;
                case "chat_unpin_message":
                    htmlAction.append("Открепил сообщение");
                    break;
                case "chat_invite_user_by_link":
                    htmlAction.append("Вступил в беседу по ссылке");
                    break;
                default:
                    htmlAction.append("Действие не поддерживается (").append(type).append(")");
                    break;
            }

            return htmlAction.append("</p>").toString();
        }

        /*
        TODO:
        1) poll (опросы)
        2) audio (аудио)
        3) audio_message (голосовое сообщение)
        4) "geo" в объекте сообщения (геометка)
        5) video (видео)
         */

        private String getHtmlForAttach(List<JSONObject> attaches) throws JSONException{
            StringBuilder rs = new StringBuilder();

            for (JSONObject attach : attaches) {
                String type = attach.getString("type");
                JSONObject item = attach.optJSONObject(type);

                if (item == null) continue;

                switch(type) {
                    case "photo":
                        var sizes = item.getJSONArray("sizes");
                        rs.append("<a href=\"").append(sizes.getJSONObject(sizes.length() - 1).getString("url")).append("\" class=\"msg-attach-link\">фотография</a>");
                        break;
                    case "doc":
                        rs.append("<a href=\"").append(item.getString("url")).append("\" class=\"msg-attach-link\">документ</a>");
                        break;
                    case "link":
                        rs.append("<a href=\"").append(item.getString("url")).append("\" class=\"msg-attach-link\">ссылка</a>");
                        break;
                    case "wall":
                        rs.append("<a href=\"https://vk.ru/wall").append(item.optString("to_id", item.optString("owner_id"))).append("_").append(item.getString("id")).append("\" class=\"msg-attach-link\">запись на стене</a>");
                        break;
                    case "sticker":
                        rs.append("<img src=\"").append(item.getJSONArray("images").getJSONObject(1).getString("url")).append("\"/>");
                        break;
                    default:
                        rs.append("Вложение не поддерживается (").append(type).append(")");
                        break;
                }

                rs.append(" ");
            }

            return rs.toString();
        }
    }
}