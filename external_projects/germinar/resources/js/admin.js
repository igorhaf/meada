import { createApp } from 'vue';
import SortableList from './components/SortableList.vue';

// Registro de componentes montáveis via <div data-vue="..." data-props='{...}'>.
const registry = {
    'sortable-list': SortableList,
};

document.querySelectorAll('[data-vue]').forEach((el) => {
    const component = registry[el.dataset.vue];
    if (!component) return;

    let props = {};
    if (el.dataset.props) {
        try {
            props = JSON.parse(el.dataset.props);
        } catch {
            console.error(`data-props inválido em [data-vue="${el.dataset.vue}"]`);
            return;
        }
    }

    createApp(component, props).mount(el);
});
