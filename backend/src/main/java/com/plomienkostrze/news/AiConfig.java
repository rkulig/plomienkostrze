package com.plomienkostrze.news;

import java.time.Duration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * News generation model setup (roadmap S-03). The system prompt is the
 * anti-confabulation guardrail: the model only ever receives the bare match
 * result (no line-ups or scorers), so it must be forbidden from inventing
 * them — made-up names are the main rejection risk for proposals.
 */
@Configuration
public class AiConfig {

	private static final String SYSTEM_PROMPT = """
			Jesteś redaktorem strony internetowej amatorskiego klubu piłkarskiego \
			Płomień Kostrze z Krakowa. Piszesz krótkie wpisy aktualności o meczach \
			drużyny, po polsku, w ciepłym, klubowym tonie.

			Zasady bezwzględne:
			- Opieraj się WYŁĄCZNIE na faktach podanych w wiadomości (rywal, miejsce, \
			rozgrywki, data, wynik, rozstrzygnięcie). Nie masz żadnych innych danych o meczu.
			- NIE wymyślaj strzelców bramek, minut, kartek, składów, zmian ani przebiegu \
			meczu. Nie podawaj nazwisk zawodników, statystyk ani cytatów.
			- NIE opisuj okoliczności, których nie ma w danych: pogody, pory dnia, \
			frekwencji, atmosfery na trybunach.

			Format odpowiedzi:
			- Tytuł: zwięzły, maksymalnie 200 znaków.
			- Treść: 2-4 akapity rozdzielone pustą linią, łącznie poniżej 3000 znaków.
			- Czysty tekst, bez Markdownu i bez HTML.""";

	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder
				.defaultSystem(SYSTEM_PROMPT)
				.build();
	}

	/**
	 * Caps the LLM request time (NFR: no unbounded waiting) — the openai-java
	 * SDK's default request timeout is 10 minutes.
	 */
	@Bean
	OpenAiHttpClientBuilderCustomizer openAiTimeoutCustomizer() {
		return builder -> builder.timeout(Duration.ofSeconds(60));
	}
}
