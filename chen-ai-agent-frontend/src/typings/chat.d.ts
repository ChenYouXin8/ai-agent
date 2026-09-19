declare namespace Chat {

	type AssistantMode = 'assistant' | 'love'

	interface Chat {
		dateTime: string
		text: string
		inversion?: boolean
		error?: boolean
		loading?: boolean
		conversationOptions?: ConversationRequest | null
		requestOptions: { prompt: string; options?: ConversationRequest | null }
	}

	interface History {
		title: string
		isEdit: boolean
		uuid: number
		/** 助手模式：智能助手 / 恋爱大师，默认智能助手 */
		mode?: AssistantMode
	}

	interface ChatState {
		active: number | null
		usingContext: boolean;
		/** 新建会话时默认使用的助手模式 */
		newChatMode: AssistantMode
		history: History[]
		chat: { uuid: number; data: Chat[] }[]
	}

	interface ConversationRequest {
		conversationId?: string
		parentMessageId?: string
	}

	interface ConversationResponse {
		conversationId: string
		detail: {
			choices: { finish_reason: string; index: number; logprobs: any; text: string }[]
			created: number
			id: string
			model: string
			object: string
			usage: { completion_tokens: number; prompt_tokens: number; total_tokens: number }
		}
		id: string
		parentMessageId: string
		role: string
		text: string
	}
}
