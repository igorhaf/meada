import { createApp } from 'vue';
import QRCode from 'qrcode';

import HeroCarousel from './components/HeroCarousel.vue';
import SearchBar from './components/SearchBar.vue';
import ServiceGallery from './components/ServiceGallery.vue';
import SlotPicker from './components/SlotPicker.vue';

// Registry of interactive "islands" mounted into server-rendered Blade markup.
const registry = {
    HeroCarousel,
    SearchBar,
    ServiceGallery,
    SlotPicker,
};

document.querySelectorAll('[data-island]').forEach((el) => {
    const Component = registry[el.dataset.island];
    if (!Component) return;

    let props = {};
    if (el.dataset.props) {
        try {
            props = JSON.parse(el.dataset.props);
        } catch (e) {
            console.warn('Island props parse error for', el.dataset.island, e);
        }
    }

    createApp(Component, props).mount(el);
});

document.querySelectorAll('canvas[data-qr]').forEach((canvas) => {
    QRCode.toCanvas(canvas, canvas.dataset.qr, { width: 224, margin: 1, color: { dark: '#173c34', light: '#ffffff' } });
});
