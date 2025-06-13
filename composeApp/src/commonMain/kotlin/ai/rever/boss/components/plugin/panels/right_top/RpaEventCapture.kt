package ai.rever.boss.components.plugin.panels.right_top

/**
 * JavaScript code for DOM event capture in RPA Recorder
 * This code is injected into the browser to capture user interactions
 */
object RpaEventCapture {
    
    /**
     * Main event capture script that gets injected into the browser
     */
    val eventCaptureScript = """
        (() => {
            // Check if already injected
            if (window.__rpaRecorderInjected) return;
            window.__rpaRecorderInjected = true;
            
            // Robust selector generator function - returns the best selector type
            function getRobustSelector(el) {
                if (!el || el.nodeType !== Node.ELEMENT_NODE) return { type: 'xpath', value: '' };

                // 1. ID first (skip dynamic IDs like GUIDs and UI framework IDs)
                const id = el.id;
                if (id && !/^[0-9a-fA-F\-]{36}$/.test(id) && !/^ui-id-\d+$/.test(id) && !/^[a-z]+\d{5,}$/.test(id)) {
                    // Return ID selector when we have a stable ID
                    return { type: 'id', value: id };
                }

                // For everything else, we'll generate an XPath
                return { type: 'xpath', value: getRobustXPath(el) };
            }
            
            // Pure XPath generator (returns just the XPath string)
            function getRobustXPath(el) {
                if (!el || el.nodeType !== Node.ELEMENT_NODE) return '';
                
                const tag = el.tagName.toLowerCase();
                const text = el.innerText?.trim() || '';
                
                // Try stable attributes first
                const stableAttrs = [
                    'data-test', 'data-testid', 'data-qa', 'data-cy',
                    'aria-label', 'role', 'name', 'title'
                ];
                const textTest = text && text.length < 50 ? `[normalize-space(text())="${'$'}{text}"]` : '';
                
                for (const attr of stableAttrs) {
                    const val = el.getAttribute(attr);
                    if (val) {
                        return `//${'$'}{tag}[@${'$'}{attr}="${'$'}{val}"]${'$'}{textTest}`;
                    }
                }

                // 3. Specific tag + exact text
                if (text && text.length < 50) {
                    if (['li', 'option', 'a', 'button', 'label'].includes(tag)) {
                        const cls = Array.from(el.classList).filter(c => c.length > 2)[0];
                        if (cls) {
                            return `//${'$'}{tag}[contains(concat(' ', normalize-space(@class), ' '), ' ${'$'}{cls} ') and normalize-space(.)="${'$'}{text}"]`;
                        }
                        return `//${'$'}{tag}[normalize-space(.)="${'$'}{text}"]`;
                    }
                    return `//${'$'}{tag}[normalize-space(text())="${'$'}{text}"]`;
                }

                // 4. Specific input/button attributes with class and value/text
                if (tag === 'input') {
                    const type = el.getAttribute('type');
                    if (type) {
                        const val = el.getAttribute('value') || el.value || '';
                        const cls = Array.from(el.classList)
                            .filter(c => /^[a-zA-Z]/.test(c) && c.length > 2)[0] || '';
                        const clsTest = cls ? ` and contains(concat(' ', normalize-space(@class), ' '), ' ${'$'}{cls} ')` : '';
                        const valTest = val ? ` and @value="${'$'}{val}"` : '';
                        return `//input[@type="${'$'}{type}"${'$'}{clsTest}${'$'}{valTest}]`;
                    }
                }
                if (tag === 'button') {
                    const type = el.getAttribute('type');
                    if (type) {
                        const val = el.innerText ? el.innerText.trim() : '';
                        const cls = Array.from(el.classList)
                            .filter(c => /^[a-zA-Z]/.test(c) && c.length > 2)[0] || '';
                        const clsTest = cls ? ` and contains(concat(' ', normalize-space(@class), ' '), ' ${'$'}{cls} ')` : '';
                        const textTest2 = val ? ` and normalize-space(text())="${'$'}{val}"` : '';
                        return `//button[@type="${'$'}{type}"${'$'}{clsTest}${'$'}{textTest2}]`;
                    }
                }

                // 5. Class-based fallback with text when available (aggressive robust selectors)
                const classList = Array.from(el.classList)
                    .filter(c => /^[a-zA-Z]/.test(c) && c.length > 2);
                if (classList.length && text) {
                    const cls = classList[0];
                    // Use class and exact element text to avoid sequence-based IDs
                    return `//${'$'}{tag}[contains(concat(' ', normalize-space(@class), ' '), ' ${'$'}{cls} ') and normalize-space(.)="${'$'}{text}"]`;
                } else if (classList.length) {
                    const cls = classList[0];
                    // Fallback to class only
                    return `//${'$'}{tag}[contains(concat(' ', normalize-space(@class), ' '), ' ${'$'}{cls} ')]`;
                }

                // 6. Positional fallback
                const parent = el.parentNode;
                if (!parent || parent.nodeType !== Node.ELEMENT_NODE) {
                    return `/${'$'}{tag}`;
                }
                const siblings = Array.from(parent.children)
                    .filter(c => c.tagName === el.tagName);
                const idx = siblings.indexOf(el) + 1;
                return getRobustXPath(parent) + `/${'$'}{tag}[${'$'}{idx}]`;
            }
            
            // Enhanced text extraction with multiple fallback strategies
            function getElementText(el) {
                let text = '';
                
                // Strategy 1: Direct text content (best for buttons, links, labels)
                if (el.innerText && el.innerText.trim()) {
                    text = el.innerText.trim();
                }
                // Strategy 2: Text content (fallback for elements without innerText)
                else if (el.textContent && el.textContent.trim()) {
                    text = el.textContent.trim();
                }
                // Strategy 3: Value for input elements
                else if (el.value && el.value.trim()) {
                    text = el.value.trim();
                }
                // Strategy 4: Placeholder for input elements
                else if (el.placeholder && el.placeholder.trim()) {
                    text = `[${'$'}{el.placeholder.trim()}]`;
                }
                // Strategy 5: aria-label for accessibility
                else if (el.getAttribute('aria-label') && el.getAttribute('aria-label').trim()) {
                    text = el.getAttribute('aria-label').trim();
                }
                // Strategy 6: title attribute
                else if (el.getAttribute('title') && el.getAttribute('title').trim()) {
                    text = el.getAttribute('title').trim();
                }
                // Strategy 7: alt text for images
                else if (el.tagName === 'IMG' && el.getAttribute('alt')) {
                    text = `[Image: ${'$'}{el.getAttribute('alt')}]`;
                }
                // Strategy 8: For buttons without text, try to describe by type/class
                else if (el.tagName === 'BUTTON' || el.tagName === 'INPUT') {
                    const type = el.getAttribute('type') || '';
                    const className = el.className || '';
                    if (type) {
                        text = `[${'$'}{type} ${'$'}{el.tagName.toLowerCase()}]`;
                    } else if (className) {
                        // Extract meaningful class names (avoid framework-generated ones)
                        const meaningfulClasses = className.split(' ')
                            .filter(cls => cls.length > 2 && !/^[a-z]+\d+/.test(cls))
                            .slice(0, 2);
                        if (meaningfulClasses.length > 0) {
                            text = `[${'$'}{meaningfulClasses.join(' ')}]`;
                        }
                    }
                }
                // Strategy 9: For links, try href description
                else if (el.tagName === 'A' && el.getAttribute('href')) {
                    const href = el.getAttribute('href');
                    text = `[Link to: ${'$'}{href}]`;
                }
                
                // Clean up and limit text length
                return text.substring(0, 100);
            }
            
            // Check selector uniqueness
            function checkSelectorUniqueness(selector, type = 'xpath') {
                try {
                    if (type === 'xpath') {
                        const result = document.evaluate(
                            selector,
                            document,
                            null,
                            XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,
                            null
                        );
                        return result.snapshotLength === 1;
                    } else if (type === 'css') {
                        const elements = document.querySelectorAll(selector);
                        return elements.length === 1;
                    } else if (type === 'id') {
                        const element = document.getElementById(selector);
                        return element !== null;
                    }
                } catch (e) {
                    return false;
                }
                return false;
            }
            
            // Send action to Kotlin
            function sendAction(action) {
                // Add uniqueness check for selectors
                if (action.selector && action.selector.value) {
                    action.selector.isUnique = checkSelectorUniqueness(action.selector.value, action.selector.type);
                }
                
                if (window.__rpaRecordAction) {
                    window.__rpaRecordAction(JSON.stringify(action));
                }
            }
            
            // Track dropdown interactions
            let dropdownInteractionInProgress = false;
            
            // Click event handler
            document.addEventListener('mousedown', e => {
                const el = e.target;
                if (!el) return;
                
                // Handle dropdowns separately
                if (el.tagName === 'SELECT' || el.tagName === 'OPTION') {
                    dropdownInteractionInProgress = true;
                    setTimeout(() => {
                        dropdownInteractionInProgress = false;
                    }, 500);
                    return;
                }
                
                const selector = getRobustSelector(el);
                const text = getElementText(el);
                
                sendAction({
                    type: 'click',
                    selector: selector,
                    elementText: text,
                    timestamp: Date.now(),
                    url: window.location.href
                });
            }, true);
            
            // Input event handler with debouncing
            const inputDebounce = {};
            document.addEventListener('input', e => {
                const el = e.target;
                if (!el || !['INPUT', 'TEXTAREA'].includes(el.tagName)) return;
                if (el.type === 'hidden') return;
                
                const key = el.tagName + '|' + (el.id || el.name || el.type);
                clearTimeout(inputDebounce[key]);
                
                inputDebounce[key] = setTimeout(() => {
                    const selector = getRobustSelector(el);
                    sendAction({
                        type: 'input',
                        selector: selector,
                        value: el.value,
                        timestamp: Date.now(),
                        url: window.location.href
                    });
                }, 300);
            }, true);
            
            // Change event listener specifically for SELECT elements - this is the main dropdown handler
            document.addEventListener('change', e => {
                const el = e.target;
                if (el.tagName !== 'SELECT') return;
                
                const selector = getRobustSelector(el);
                const selectedOption = el.options[el.selectedIndex];
                const optionText = selectedOption ? selectedOption.textContent.trim() : '';
                const optionValue = selectedOption ? selectedOption.value : '';
                
                // Use the visible text as the value for the select action
                // This matches how the RPA engine tries select_by_visible_text first
                const selectValue = optionText || optionValue;
                
                if (selectValue) {
                    sendAction({
                        type: 'select',
                        selector: selector,
                        value: selectValue,
                        element_type: 'select_dropdown',
                        elementText: optionText,
                        timestamp: Date.now(),
                        url: window.location.href
                    });
                }
            }, true);
            
            // Navigation detection
            let lastUrl = window.location.href;
            const checkNavigation = () => {
                if (window.location.href !== lastUrl) {
                    lastUrl = window.location.href;
                    sendAction({
                        type: 'navigation',
                        url: lastUrl,
                        timestamp: Date.now()
                    });
                }
            };
            
            // Check for navigation changes periodically
            setInterval(checkNavigation, 500);
            
            // Also detect navigation via History API
            const originalPushState = history.pushState;
            const originalReplaceState = history.replaceState;
            
            history.pushState = function() {
                originalPushState.apply(history, arguments);
                setTimeout(checkNavigation, 0);
            };
            
            history.replaceState = function() {
                originalReplaceState.apply(history, arguments);
                setTimeout(checkNavigation, 0);
            };
            
            window.addEventListener('popstate', checkNavigation);
            
            // Scroll event capture (debounced)
            let scrollTimeout;
            let lastScrollPos = { x: 0, y: 0 };
            window.addEventListener('scroll', function() {
                clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(() => {
                    const currentX = window.pageXOffset || document.documentElement.scrollLeft;
                    const currentY = window.pageYOffset || document.documentElement.scrollTop;
                    
                    // Only record significant scrolls
                    if (Math.abs(currentX - lastScrollPos.x) > 50 || Math.abs(currentY - lastScrollPos.y) > 50) {
                        lastScrollPos = { x: currentX, y: currentY };
                        sendAction({
                            type: 'scroll',
                            selector: { type: 'none', value: null },
                            value: `${'$'}{currentX},${'$'}{currentY}`,
                            timestamp: Date.now()
                        });
                    }
                }, 500); // Wait 500ms after scroll stops
            }, true);
            
            console.log('RPA Recorder event capture initialized');
        })();
    """.trimIndent()
    
    /**
     * Script to remove event listeners
     */
    val removeEventListenersScript = """
        (() => {
            if (window.__rpaRecorderInjected) {
                window.__rpaRecorderInjected = false;
                window.__rpaRecordAction = null;
                console.log('RPA Recorder event capture removed');
            }
        })();
    """.trimIndent()
    
    /**
     * Script to validate selector uniqueness
     */
    val selectorValidationScript = """
        function validateSelector(selector, type) {
            try {
                let elements;
                if (type === 'xpath') {
                    elements = document.evaluate(
                        selector,
                        document,
                        null,
                        XPathResult.ORDERED_NODE_SNAPSHOT_TYPE,
                        null
                    );
                    return {
                        isValid: true,
                        count: elements.snapshotLength,
                        isUnique: elements.snapshotLength === 1
                    };
                } else if (type === 'css') {
                    elements = document.querySelectorAll(selector);
                    return {
                        isValid: true,
                        count: elements.length,
                        isUnique: elements.length === 1
                    };
                }
            } catch (e) {
                return {
                    isValid: false,
                    count: 0,
                    isUnique: false,
                    error: e.message
                };
            }
        }
    """.trimIndent()
    
    /**
     * Script to convert CSS selector to XPath
     */
    val cssToXPathScript = """
        function cssToXPath(cssSelector) {
            try {
                const element = document.querySelector(cssSelector);
                if (!element) {
                    return null;
                }
                
                function getXPath(node) {
                    if (node.id) {
                        return '//*[@id="' + node.id + '"]';
                    }
                    const parts = [];
                    while (node && node.nodeType === Node.ELEMENT_NODE) {
                        let index = 1;
                        let sib = node.previousSibling;
                        while (sib) {
                            if (sib.nodeType === Node.ELEMENT_NODE && sib.nodeName === node.nodeName) {
                                index++;
                            }
                            sib = sib.previousSibling;
                        }
                        const tag = node.nodeName.toLowerCase();
                        parts.unshift(`${'$'}{tag}[${'$'}{index}]`);
                        node = node.parentNode;
                    }
                    return '/' + parts.join('/');
                }
                
                return getXPath(element);
            } catch (e) {
                return null;
            }
        }
    """.trimIndent()
}