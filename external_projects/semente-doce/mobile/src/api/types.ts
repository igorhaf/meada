/**
 * Tipos da API v1 (backend/routes/api.php). CONTRATO: o backend serializa
 * exatamente estas chaves (camelCase). Não renomear de um lado só.
 */

export type Category = {
    id: number;
    name: string;
    slug: string;
    icon: string | null;
    accent: string | null;
    description: string | null;
    children: Category[];
};

export type OptionChoice = {
    id: number;
    name: string;
    delta: number;
};

export type OptionGroup = {
    id: number;
    name: string;
    minSelect: number;
    maxSelect: number;
    required: boolean;
    options: OptionChoice[];
};

export type Product = {
    id: number;
    title: string;
    slug: string;
    description: string | null;
    unit: string;
    unitLabel: string;
    priceSuffix: string;
    flavor: string | null;
    serves: string | null;
    allergens: string | null;
    minQty: number;
    madeToOrder: boolean;
    leadTimeDays: number | null;
    prepMinutes: number | null;
    price: number;
    compareAtPrice: number | null;
    discountPercent: number | null;
    image: string;
    rating: number;
    reviewsCount: number;
    soldCount: number;
    featured: boolean;
    categorySlug: string | null;
    /** Presente só no detalhe (GET /produtos/{slug}). */
    optionGroups?: OptionGroup[];
};

export type KitItem = {
    label: string;
    qty: number;
    unitPrice: number;
};

export type Kit = {
    id: number;
    name: string;
    slug: string;
    description: string | null;
    type: string;
    typeLabel: string;
    serves: string | null;
    price: number;
    componentsTotal: number;
    savings: number;
    image: string;
    madeToOrder: boolean;
    leadTimeDays: number | null;
    featured: boolean;
    items: KitItem[];
};

export type StoreInfo = {
    name: string;
    tagline: string | null;
    announcement: string | null;
    whatsapp: string | null;
    phone: string | null;
    email: string | null;
    address: string | null;
    openingHours: string | null;
    instagram: string | null;
    delivery: {
        pickupEnabled: boolean;
        minOrder: number | null;
        freeAbove: number | null;
        defaultFee: number;
        etaMin: number;
        etaMax: number;
        origin: string;
    };
};

export type Paginated<T> = {
    data: T[];
    meta: {
        currentPage: number;
        lastPage: number;
        total: number;
    };
};

export type CustomOrderInput = {
    customerName: string;
    customerPhone: string;
    customerEmail?: string;
    title: string;
    description: string;
    quantity?: number;
    flavor?: string;
    messageOnItem?: string;
    eventDate: string; // YYYY-MM-DD
    fulfillmentType: 'pickup' | 'delivery';
    deliveryAddress?: string;
    productSlug?: string;
    kitSlug?: string;
};

export type CustomOrderReceipt = {
    reference: string;
    status: string;
    statusLabel: string;
    title: string;
};
