
package com.autofill.jobapp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatModel;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
		return "index";
	}

	@PostMapping("/generate")
	public String generateQuote(@RequestParam String topic, HttpServletRequest request,  HttpServletResponse response,Model model) {

		if (topic == null || topic.trim().isEmpty()) {
			model.addAttribute("error", " Please enter a topic to generate a quote.");
			return "index";
		}
		// Check if cookie already exists
		boolean cookieUsed = false;
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if ("usedFreeQuote".equals(cookie.getName()) && "true".equals(cookie.getValue())) {
					cookieUsed = true;
					break;
				}
			}
		}
		// if already used show this messege
		if (cookieUsed) {
			model.addAttribute("quote",
					"""
							    You’ve already used your free quote.
							    To generate unlimited quotes, clone this project from GitHub and run it locally using your own OpenAI API key.
							""");
			return "index";
		}
//		String ip = request.getRemoteAddr();
		String ip = request.getHeader("X-Forwarded-For");
		if (ip == null) {
			ip = request.getRemoteAddr(); // fallback
		} else if (ip.contains(",")) {
			ip = ip.split(",")[0]; // get first IP if multiple in chain
		}
		ip = ip.trim();

		// If user already used their free quote
		if (userUsedFreeQuote.getOrDefault(ip, false)) {
			String message = """
					     You’ve reached your free quote limit.
					     To generate unlimited quotes, clone this project from GitHub and run it locally using your own OpenAI API key.

					""";
			model.addAttribute("quote", message);
			return "index";
		}
		try {
			// First-time: generate actual quote
			PromptTemplate promptTemplate = new PromptTemplate("Give me a motivational quote about {topic}");
			Prompt prompt = promptTemplate.create(Map.of("topic", topic));
			ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();

			String quote = chatResponse.getResult().getOutput().getText();
			model.addAttribute("quote", quote);
			model.addAttribute("topic", topic);

			userUsedFreeQuote.put(ip, true);// mark as used
			Cookie cookie = new Cookie("usedFreeQuote", "true");
			cookie.setPath("/");
			response.addCookie(cookie);

		} catch (Exception e) {
			System.out.println("OpenAI API failed: " + e.getMessage());
			model.addAttribute("quote", " Sorry! Unable to generate quote right now. Try again later.");
		}
		return "index";
	}

}
