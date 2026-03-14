package ai.personal.secretary.dto;

public class ChatResponse {

    private Long conversationId;
    private String answer;

    public ChatResponse() {
    }

    public ChatResponse(Long conversationId, String answer) {
        this.conversationId = conversationId;
        this.answer = answer;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}