
package com.autofill.jobapp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class QuoteController {

	private final ChatClient chatClient;

	// Track IPs that already used their free quote
	private final Map<String, Boolean> userUsedFreeQuote = new ConcurrentHashMap<>();

	public QuoteController(OpenAiChatModel chatModel) {
		this.chatClient = ChatClient.create(chatModel);
	}

	@GetMapping("/")
	public String home() {
		System.out.println(">>> QuoteController loaded - / endpoint hit");
		return "index";
	}

	@PostMapping("/generate")
	public String generateQuote(@RequestParam String topic, HttpServletRequest request, Model model) {

		if (topic == null || topic.trim().isEmpty()) {
			model.addAttribute("error", " Please enter a topic to generate a quote.");
			return "index";
		}
		String ip = request.getRemoteAddr();

		// If user already used their free quote
		if (userUsedFreeQuote.getOrDefault(ip, false)) {
			String message = """
					     You’ve reached your free quote limit.
					     To generate unlimited quotes, clone this project from GitHub and run it locally using your own OpenAI API key.

					""";
			model.addAttribute("quote", message);
			return "index";
		}

		// First-time: generate actual quote
		PromptTemplate promptTemplate = new PromptTemplate("Give me a motivational quote about {topic}");
		Prompt prompt = promptTemplate.create(Map.of("topic", topic));
		ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();

		String quote = chatResponse.getResult().getOutput().getText();
		model.addAttribute("quote", quote);
		model.addAttribute("topic", topic);

		userUsedFreeQuote.put(ip, true); // mark as used
		return "index";
	}

}
