#include "../../main.h"
#include "../gui.h"
#include "button.h"

extern bool OpenButton;

// ================= CORES PADRÃO (PRETO) =================
static ImColor COLOR_BUTTON        = ImColor(0, 0, 0, 255);     // Preto
static ImColor COLOR_BUTTON_FOCUS  = ImColor(25, 25, 25, 255);  // Preto mais claro
static ImColor COLOR_OUTLINE       = ImColor(0, 0, 0, 255);     // Outline preta
// ========================================================


// ===================== Button ===========================
Button::Button(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(255, 255, 255, 255), false, font_size);
	this->addChild(m_label);

	m_color = COLOR_BUTTON;
	m_colorFocused = COLOR_BUTTON_FOCUS;
}

void Button::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding));

	m_label->setPosition((size() - m_label->size()) / 2);
}

void Button::draw(ImGuiRenderer* renderer)
{
	renderer->drawRect(
		absolutePosition(),
		absolutePosition() + size(),
		focused() ? m_colorFocused : m_color,
		true
	);

	// Outline preta
	renderer->drawRect(
		absolutePosition() + ImVec2(UISettings::outlineSize(), UISettings::outlineSize()),
		(absolutePosition() + size()) - ImVec2(UISettings::outlineSize(), UISettings::outlineSize()),
		COLOR_OUTLINE,
		false,
		UISettings::outlineSize()
	);

	Widget::draw(renderer);
}

void Button::touchPopEvent()
{
	if (m_callback)
		m_callback();
}
// =======================================================


// ===================== CButton ==========================
CButton::CButton(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(255, 255, 255, 255), false, font_size);
	this->addChild(m_label);

	m_color = COLOR_BUTTON;
	m_colorFocused = COLOR_BUTTON_FOCUS;
}

void CButton::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding));

	m_label->setPosition((size() - m_label->size()) / 2);
}

void CButton::draw(ImGuiRenderer* renderer)
{
	if (!OpenButton)
		return;

	renderer->drawRect(
		absolutePosition(),
		absolutePosition() + size(),
		focused() ? m_colorFocused : m_color,
		true
	);

	renderer->drawRect(
		absolutePosition() + ImVec2(UISettings::outlineSize(), UISettings::outlineSize()),
		(absolutePosition() + size()) - ImVec2(UISettings::outlineSize(), UISettings::outlineSize()),
		COLOR_OUTLINE,
		false,
		UISettings::outlineSize()
	);

	Widget::draw(renderer);
}

void CButton::touchPopEvent()
{
	if (m_callback)
		m_callback();
}
// =======================================================


// ===================== OButton ==========================
OButton::OButton(const std::string& caption, float font_size)
{
	m_callback = nullptr;

	m_label = new Label(caption, ImColor(255, 255, 255, 255), false, font_size);
	this->addChild(m_label);

	m_color = COLOR_BUTTON;
	m_colorFocused = COLOR_BUTTON_FOCUS;
}

void OButton::performLayout()
{
	float padding = UISettings::padding();

	m_label->performLayout();
	this->setSize(m_label->size() + ImVec2(padding * 2, padding));

	m_label->setPosition((size() - m_label->size()) / 2);
}

void OButton::draw(ImGuiRenderer* renderer)
{
	if (OpenButton)
	{
		// Esconde o botão
		this->setPosition(ImVec2(-150.0f, -150.0f));
		return;
	}

	renderer->drawRect(
		absolutePosition(),
		absolutePosition() + size(),
		focused() ? m_colorFocused : m_color,
		true
	);

	renderer->drawRect(
		absolutePosition() + ImVec2(UISettings::outlineSize(), UISettings::outlineSize()),
		(absolutePosition() + size()) - ImVec2(UISettings::outlineSize(), UISettings::outlineSize()),
		COLOR_OUTLINE,
		false,
		UISettings::outlineSize()
	);

	Widget::draw(renderer);

	// Posição padrão
	this->setPosition(ImVec2(15.0f, 15.0f));
}

void OButton::touchPopEvent()
{
	if (m_callback)
		m_callback();
}
// =======================================================